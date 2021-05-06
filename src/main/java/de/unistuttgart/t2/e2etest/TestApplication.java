package de.unistuttgart.t2.e2etest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestTemplate;

import de.unistuttgart.t2.inventory.repository.ProductRepository;
import de.unistuttgart.t2.repository.OrderRepository;
import io.eventuate.tram.sagas.spring.orchestration.SagaOrchestratorConfiguration;
import io.eventuate.tram.spring.consumer.kafka.EventuateTramKafkaMessageConsumerConfiguration;
import io.eventuate.tram.spring.messaging.producer.jdbc.TramMessageProducerJdbcConfiguration;


@Configuration
@EnableJpaRepositories
@EnableAutoConfiguration
@Import({ TramMessageProducerJdbcConfiguration.class, 
        EventuateTramKafkaMessageConsumerConfiguration.class,
        SagaOrchestratorConfiguration.class })
@EnableMongoRepositories(basePackageClasses = {OrderRepository.class, ProductRepository.class})
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new 
                SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE).run();
              System.out.println("Spring Boot application started");
              ctx.close();
    }
    
    @Bean
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }
}
