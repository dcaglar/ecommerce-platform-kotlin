package com.dogancaglar.paymentservice.adapter.inbound.rest

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
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
                    // internal system (checkout) creates payments
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAuthority("payment:write")
                    // seller self-balance
                    .requestMatchers(HttpMethod.GET, "/api/v1/sellers/me/balance").hasRole("SELLER")
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

        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthorityPrefix("ROLE_") // Spring expects ROLE_ prefix when using hasRole()
            setAuthoritiesClaimName("realm_access.roles")
        }

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        return converter
    }
}