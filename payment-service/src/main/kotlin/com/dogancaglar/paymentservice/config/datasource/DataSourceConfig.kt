import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import javax.sql.DataSource

@Configuration
class DataSourceConfig {

    @Primary
    @Bean(name = ["dataSource"])
    @ConfigurationProperties(prefix = "spring.datasource")
    fun dataSource(): DataSource = DataSourceBuilder.create().build()

    @Bean(name = ["replicaDataSource"])
    @ConfigurationProperties(prefix = "replica-datasource")
    fun replicaDataSource(): DataSource = DataSourceBuilder.create().build()

    @Primary
    @Bean(name = ["entityManagerFactory"])
    fun entityManagerFactory(
        @Qualifier("dataSource") dataSource: DataSource,
        jpaProperties: JpaProperties
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.setPackagesToScan("com.dogancaglar.paymentservice.adapter.persistence")
        factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
        factory.setJpaPropertyMap(jpaProperties.properties)
        return factory
    }

    @Bean(name = ["replicaEntityManagerFactory"])
    fun replicaEntityManagerFactory(
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
        jpaProperties: JpaProperties
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = replicaDataSource
        factory.setPackagesToScan("com.dogancaglar.paymentservice.adapter.persistence")
        factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
        factory.setJpaPropertyMap(jpaProperties.properties)
        return factory
    }

    @Primary
    @Bean
    fun transactionManager(
        @Qualifier("entityManagerFactory") emf: EntityManagerFactory
    ) = JpaTransactionManager(emf)

    @Bean(name = ["replicaTransactionManager"])
    fun replicaTransactionManager(
        @Qualifier("replicaEntityManagerFactory") emf: EntityManagerFactory
    ) = JpaTransactionManager(emf)
}