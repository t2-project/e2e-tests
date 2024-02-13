package de.unistuttgart.t2.e2etest;

import de.unistuttgart.t2.order.repository.OrderRepository;
import io.eventuate.tram.sagas.orchestration.SagaInstanceRepository;
import io.eventuate.tram.sagas.spring.orchestration.SagaOrchestratorConfiguration;
import io.eventuate.tram.spring.consumer.kafka.EventuateTramKafkaMessageConsumerConfiguration;
import io.eventuate.tram.spring.messaging.producer.jdbc.TramMessageProducerJdbcConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

/**
 * Tests the entire T2-Project.
 * <p>
 * Needs all those saga configuration because {@link TestService} uses the {@link SagaInstanceRepository} Interface from
 * the eventuate framework to access the saga instance database.
 *
 * @author maumau
 */
@Configuration
@EntityScan(basePackages = "de.unistuttgart.t2.inventory")
@Import({TramMessageProducerJdbcConfiguration.class,
    EventuateTramKafkaMessageConsumerConfiguration.class,
    SagaOrchestratorConfiguration.class})
@EnableTransactionManagement
@EnableMongoRepositories(basePackageClasses = OrderRepository.class)
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }
}
