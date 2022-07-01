package de.unistuttgart.t2.e2etest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;

import de.unistuttgart.t2.common.BaseScan;
import de.unistuttgart.t2.inventory.repository.*;
import de.unistuttgart.t2.order.repository.OrderRepository;
import io.eventuate.tram.sagas.orchestration.SagaInstanceRepository;
import io.eventuate.tram.sagas.spring.orchestration.SagaOrchestratorConfiguration;
import io.eventuate.tram.spring.consumer.kafka.EventuateTramKafkaMessageConsumerConfiguration;
import io.eventuate.tram.spring.messaging.producer.jdbc.TramMessageProducerJdbcConfiguration;

/**
 * Tests the entire T2 store.
 * <p>
 * Needs all those saga configuration because {@link TestService} uses the {@link SagaInstanceRepository} Interface from
 * the eventuate framework to access the saga instance database.
 *
 * @author maumau
 */
@Configuration
@EnableJpaRepositories(basePackageClasses = { ProductRepository.class, ReservationRepository.class })
@EntityScan(basePackages = "de.unistuttgart.t2.inventory")
@EnableAutoConfiguration
@Import({ TramMessageProducerJdbcConfiguration.class,
          EventuateTramKafkaMessageConsumerConfiguration.class,
          SagaOrchestratorConfiguration.class })
@EnableTransactionManagement
@EnableMongoRepositories(basePackageClasses = OrderRepository.class)
@SpringBootApplication(scanBasePackageClasses = BaseScan.class)
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public RestTemplate getRestTemplate() {
        return new RestTemplate();
    }
}
