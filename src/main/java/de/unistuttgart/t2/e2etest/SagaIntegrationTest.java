package de.unistuttgart.t2.e2etest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unistuttgart.t2.common.saga.SagaRequest;
import de.unistuttgart.t2.inventory.repository.InventoryItem;
import de.unistuttgart.t2.inventory.repository.ProductRepository;
import de.unistuttgart.t2.repository.OrderItem;
import de.unistuttgart.t2.repository.OrderRepository;
import de.unistuttgart.t2.repository.OrderStatus;
import io.eventuate.tram.sagas.orchestration.SagaInstance;
import io.eventuate.tram.sagas.orchestration.SagaInstanceRepository;

/**
 * integration test the saga - i guess???
 * 
 * @author maumau
 *
 */
@Component
public class SagaIntegrationTest {
    
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();
    
    @Value("${t2.e2etest.orchestrator.url}")
    String orchestrator;
    
    @Value("${t2.e2etest.failurerate.url}")
    String failurerate;
    
    @Value("${t2.e2etest.timeoutrate.url}")
    String timeoutrate;
    
    @Value("${t2.e2etest.timeout.url}")
    String timeout;

    @Autowired
    RestTemplate template;
    @Autowired
    SagaInstanceRepository sagaRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ProductRepository productRepository;

    @PostConstruct
    public void test() {
        executethings(() -> sagaSuccessTest(), "sagaSuccessTest");
        executethings(() -> sagaFailureTest(), "sagaFailureTest");
        executethings(() -> sagaTimeoutTest(), "sagaTimeoutTest");
    }

    public void executethings(Runnable r, String name) {
        LOG.info(String.format("running %s", name));
        try {
            r.run();
            LOG.info(String.format("finished %s without failure", name));
        } catch (Throwable e) {
            e.printStackTrace();
            LOG.info(String.format("finished %s with failure : %s ", name, e.getMessage()));
        }
    }

    public void sagaSuccessTest() {
        setUpSuccess();

        String sagaid = postToOrchestrator();

        SagaInstance sagainstance = getFinishedSagaInstance(sagaid);

        assertOrderStatus(sagainstance, OrderStatus.SUCCESS);
    }

    public void sagaFailureTest() {
        setUpFailure();

        String sagaid = postToOrchestrator();

        SagaInstance sagainstance = getFinishedSagaInstance(sagaid);

        assertOrderStatus(sagainstance, OrderStatus.FAILURE);
    }

    public void sagaTimeoutTest() {
        setUpTimeout();

        String sagaid = postToOrchestrator();

        SagaInstance sagainstance = getFinishedSagaInstance(sagaid);

        assertOrderStatus(sagainstance, OrderStatus.FAILURE);
    }

    public void sagaRuntimeTest(String sagaid) {
        SagaInstance sagainstance = getFinishedSagaInstance(sagaid);
        
        assertOrderStatus(sagainstance, getStatus());
    }
    
    
    public void InteractionWithUIBackendTest() {

        SagaRequest req = new SagaRequest();

        // talk to uibackend
        // add items: cart, inventory
        String ressourceUrl = "http://localhost:8081/uibackend";
        SagaRequest request = new SagaRequest("sessionId", "cardNumber", "cardOwner", "foo", 42);
        String sagaid = template.postForEntity(ressourceUrl, request, String.class).getBody();

    }

    public void foo() {
        // this works as long as i put everything to the same db.
        List<OrderItem> orders = orderRepository.findAll();
        List<InventoryItem> products = productRepository.findAll();

        // extract order id from JSON :
        // {"cardNumber":"cardNumber","cardOwner":"cardOwner","checksum":"checksum","sessionId":"sessionId","total":42.0}

    }

    /**
     * TODO : better timeout exception
     * 
     * @param sagaid
     * @return
     */
    private SagaInstance getFinishedSagaInstance(String sagaid) {
        String sagatype = "de.unistuttgart.t2.orchestrator.saga.Saga";

        for (int i = 0; i < 20; i++) {
            SagaInstance actual = sagaRepository.find(sagatype, sagaid);

            if (actual.getStateName().contains("true")) {
                return actual;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // TODO this is bad. make better
        throw new RuntimeException(String.format("Saga %s did not finish within %d seconds.", sagaid, 20 * 5));
    }

    private String postToOrchestrator() {
        SagaRequest request = new SagaRequest("sessionId", "cardNumber", "cardOwner", "sessionId", 42);
        String sagaid = template.postForEntity(orchestrator, request, String.class).getBody();
        return sagaid;
    }

    private void assertOrderStatus(SagaInstance sagainstance, OrderStatus expected) {
        String sessionId = getSessionId(sagainstance.getSerializedSagaData().getSagaDataJSON());
        String orderId = getOrderId(sagainstance.getSerializedSagaData().getSagaDataJSON());

        Optional<OrderItem> order = orderRepository.findById(orderId);
        assertTrue(order.isPresent(), String.format("missing order with orderid %s", orderId));
        assertEquals(sessionId, order.get().getSessionId(),
                String.format("wrong sessionId for order %s", orderId));
        assertEquals(expected, order.get().getStatus(),
                String.format("wrong order status for order %s", orderId));
    }

    private Set<String> getSessionIdsFromReservations() {
        List<InventoryItem> items = productRepository.findAll();
        Set<String> sessionIds = items.stream().map(i -> i.getReservations().keySet()).reduce(new HashSet<String>(),
                (a, b) -> {
                    a.addAll(b);
                    return a;
                });
        return sessionIds;
    }
    
    /**
     * TODO : maybe loop payment back here, such that im sure about reply :x 
     * 
     * 
     * @return
     */
    private OrderStatus getStatus() {
        //TODO talk to credit institute
        
        return OrderStatus.SUCCESS;
    }

    /**
     * configure credit institute, such that payment step of saga succeeds
     */
    private void setUpSuccess() {
        assertEquals(0, template.getForEntity(timeoutrate + "0", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(0, template.getForEntity(failurerate + "0", Double.class).getBody(), "failed to set failurerate");
    }

    /**
     * configure credit institute such that payment step of saga fails
     */
    private void setUpFailure() {
        assertEquals(0, template.getForEntity(timeoutrate + "0", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(1, template.getForEntity(failurerate + "1", Double.class).getBody(), "failed to set failurerate");
    }

    /**
     * configure credit institute such that payment step of saga timeouts
     */
    private void setUpTimeout() {
        assertEquals(1, template.getForEntity(timeoutrate + "1", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(0, template.getForEntity(failurerate + "0", Double.class).getBody(), "failed to set failurerate");
        assertEquals(4000, template.getForEntity(timeout + "4000", Double.class).getBody(), "failed to set delay");
    }


    /**
     * 
     * @param json
     * @return
     */
    private String getSessionId(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return mapper.treeToValue(root.path("sessionId"), String.class);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException("parsing json failed", e);
        }
    }

    /**
     * 
     * @param json
     * @return
     */
    private String getOrderId(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return mapper.treeToValue(root.path("orderId"), String.class);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            
            throw new RuntimeException("parsing json failed",e);
        }
    }
}