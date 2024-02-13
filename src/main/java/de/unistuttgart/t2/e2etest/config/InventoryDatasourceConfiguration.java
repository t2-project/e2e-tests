package de.unistuttgart.t2.e2etest.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class InventoryDatasourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.inventory")
    public DataSourceProperties inventoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource inventoryDataSource() {
        return inventoryDataSourceProperties()
            .initializeDataSourceBuilder()
            .build();
    }
}
