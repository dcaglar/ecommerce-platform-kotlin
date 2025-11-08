package com.dogancaglar.paymentservice.adapter.inbound.rest

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    // Allow actuator endpoints (health, liveness, readiness, prometheus) without authentication
                    .requestMatchers("/actuator/**").permitAll()
                    // internal system (checkout) creates payments
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAuthority("payment:write")
                    // seller self-balance (Case 1: user via frontend, Case 3: merchant API via client credentials)
                    .requestMatchers(HttpMethod.GET, "/api/v1/sellers/me/balance").hasAnyRole("SELLER", "SELLER_API")
                    // finance/admin back-office - balance queries
                    .requestMatchers(HttpMethod.GET, "/api/v1/sellers/*/balance").hasAnyRole("FINANCE", "ADMIN")
                    // finance/admin back-office - other seller endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/sellers/**").hasAnyRole("FINANCE", "ADMIN")
                    // default: deny everything else unless explicitly allowed
                    .anyRequest().denyAll()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    // Return 401 Unauthorized when authentication fails (no valid JWT)
                    .authenticationEntryPoint { request, response, authException ->
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = "application/json"
                        response.writer.write("""
                            {
                                "timestamp": "${java.time.Instant.now()}",
                                "status": 401,
                                "error": "Unauthorized",
                                "message": "Authentication required. Please provide a valid JWT token.",
                                "path": "${request.requestURI}"
                            }
                        """.trimIndent())
                    }
                    // Return 403 Forbidden when authorization fails (valid JWT but insufficient permissions)
                    .accessDeniedHandler { request, response, accessDeniedException ->
                        response.status = HttpStatus.FORBIDDEN.value()
                        response.contentType = "application/json"
                        response.writer.write("""
                            {
                                "timestamp": "${java.time.Instant.now()}",
                                "status": 403,
                                "error": "Forbidden",
                                "message": "Access denied. You do not have the required permissions.",
                                "path": "${request.requestURI}"
                            }
                        """.trimIndent())
                    }
            }
            .csrf { it.disable() }
            .cors { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .oauth2ResourceServer { oauth ->
                oauth.jwt { jwtConfigurer ->
                    jwtConfigurer.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                }
            }

        return http.build()
    }

    private fun keycloakJwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()

        // Custom converter that handles both roles (with ROLE_ prefix) and authorities (without prefix)
        val customConverter = Converter<Jwt, Collection<GrantedAuthority>> { jwt ->
            val roles = jwt.claims["realm_access"] as? Map<*, *>
            val roleList = roles?.get("roles") as? List<*>
            
            val authorities = mutableListOf<GrantedAuthority>()
            
            roleList?.forEach { role ->
                val roleName = role.toString()
                // Add authority without prefix (for hasAuthority checks like "payment:write")
                authorities.add(SimpleGrantedAuthority(roleName))
                // Add role with ROLE_ prefix (for hasRole checks like "SELLER", "FINANCE")
                // Only add ROLE_ prefix if it doesn't already have it and if it's a role-like name
                if (!roleName.startsWith("ROLE_") && !roleName.contains(":")) {
                    authorities.add(SimpleGrantedAuthority("ROLE_$roleName"))
                }
            }
            
            authorities
        }

        converter.setJwtGrantedAuthoritiesConverter(customConverter)
        return converter
    }
}