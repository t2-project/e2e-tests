package de.unistuttgart.t2.e2etest.config;

import de.unistuttgart.t2.inventory.repository.ProductRepository;
import de.unistuttgart.t2.inventory.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackageClasses = {ProductRepository.class, ReservationRepository.class},
    entityManagerFactoryRef = "inventoryEntityManagerFactory",
    transactionManagerRef = "inventoryTransactionManager"
)
public class InventoryJpaConfiguration {

    @Bean
    public LocalContainerEntityManagerFactoryBean inventoryEntityManagerFactory(
        @Qualifier("inventoryDataSource") DataSource dataSource,
        EntityManagerFactoryBuilder builder) {
        return builder
            .dataSource(dataSource)
            .packages(ProductRepository.class, ReservationRepository.class)
            .build();
    }

    @Bean
    public PlatformTransactionManager inventoryTransactionManager(
        @Qualifier("inventoryEntityManagerFactory") LocalContainerEntityManagerFactoryBean inventoryEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(inventoryEntityManagerFactory.getObject()));
    }
}