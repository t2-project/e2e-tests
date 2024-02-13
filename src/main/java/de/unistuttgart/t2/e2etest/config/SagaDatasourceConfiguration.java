package de.unistuttgart.t2.e2etest.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class SagaDatasourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.saga")
    public DataSourceProperties sagaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource sagaDataSource() {
        return sagaDataSourceProperties()
            .initializeDataSourceBuilder()
            .build();
    }

    @Bean
    public JdbcTemplate sagaJdbcTemplate(@Qualifier("sagaDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
