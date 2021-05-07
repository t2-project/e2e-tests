package de.unistuttgart.t2.e2etest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
public class TestService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    @Value("${t2.e2etest.orchestrator.url}")
    String orchestrator;

// for later usage....?
//    @Autowired
//    ProviderConfigHelper helper;
    @Autowired
    RestTemplate template;
    @Autowired
    SagaInstanceRepository sagaRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ProductRepository productRepository;

    // i'm not sure how conncurent spring stuff is, so im using a concurrent but...
    // better safe than sorry or something like that.
    static Map<String, OrderStatus> correlationToStatus = new ConcurrentHashMap<>();
    static Map<String, String> correlationToSaga = new ConcurrentHashMap<>();

    @PostConstruct
    public void test() {
        // mandatorily run each once.
        executethings(() -> initTest(), "sagaSuccessTest");
    }

    /**
     * execute something an prettily print info about failures and exceptions.
     * 
     * @param r    thing to run
     * @param name for the sake of displaying something
     */
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

    /**
     * TODO
     */
    public void initTest() {
        SagaRequest request = new SagaRequest("sessionId", "cardNumber", "cardOwner", "sessionId", 42);

        String sagaid = postToOrchestrator(request);
        SagaInstance sagainstance = getFinishedSagaInstance(sagaid);

        assertOrderStatus(sagainstance, correlationToStatus.get(sagaid));

        // can not assert reservations, because no ui backend interaction
    }

    /**
     * test sagas at runtime
     * 
     * @param request the saga request
     */
    public void sagaRuntimeTest(String sagaid) {
        SagaInstance sagainstance = getFinishedSagaInstance(correlationToSaga.get(sagaid));

        assertOrderStatus(sagainstance, correlationToStatus.get(sagaid));
        assertReservationStatus(sagainstance, correlationToStatus.get(sagaid));
    }

    /**
     * get saga instance with given id but only if its finished.
     * 
     * poll saga db repeatedly until either the saga instance transitioned into end
     * state. if it doesnt reach the end state within a certain time, it throws an
     * exception.
     * 
     * TODO : better timeout exception
     * 
     * @param sagaid
     * @return saga instance in end state
     */
    private SagaInstance getFinishedSagaInstance(String sagaid) {
        String sagatype = "de.unistuttgart.t2.orchestrator.saga.Saga";

        for (int i = 0; i < 20; i++) {
            SagaInstance actual = sagaRepository.find(sagatype, sagaid);

            // isEndState() always returns 'false' (even though the saga instance has very
            // clearly reached the endstate) thus test with stateName.
            if (actual.getStateName().contains("true")) {
                return actual;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // TODO this is bad exception. make better
        throw new RuntimeException(String.format("Saga %s did not finish within %d seconds.", sagaid, 20 * 5));
    }

    /**
     * send post request to start a saga to the orchestrator
     * 
     * @param request the request to send
     * @return id of started saga instance
     */
    public String postToOrchestrator(SagaRequest request) {
        String sagaid = template.postForEntity(orchestrator, request, String.class).getBody();
        return sagaid;
    }

    /**
     * assert presence or absence of reservations belonging to given saga instance.
     * 
     * if expected is SUCCESS, the reservations should be absent (i.e. commited),
     * otherwise they should be present.
     * 
     * @param sagainstance the saga instance under test
     * @param expected     outcome to test for
     */
    private void assertReservationStatus(SagaInstance sagainstance, OrderStatus expected) {
        String sessionId = getSessionId(sagainstance.getSerializedSagaData().getSagaDataJSON());
        Set<String> sessionIds = getSessionIdsFromReservations();

        assertFalse(sessionIds.contains(sessionId),
                String.format("reservations for saga instance %s not deleted.", sagainstance.getId()));

        // i will not assert, that the product unis were updated (or not) because that
        // is inventory internal and thus not part of e2e.
    }

    /**
     * assert correctness of order belonging to given saga instance.
     * 
     * @param sagainstance the saga instance under test
     * @param expected     outcome to test for
     */
    private void assertOrderStatus(SagaInstance sagainstance, OrderStatus expected) {
        String sessionId = getSessionId(sagainstance.getSerializedSagaData().getSagaDataJSON());
        String orderId = getOrderId(sagainstance.getSerializedSagaData().getSagaDataJSON());

        Optional<OrderItem> order = orderRepository.findById(orderId);
        assertTrue(order.isPresent(), String.format("missing order with orderid %s", orderId));
        assertEquals(sessionId, order.get().getSessionId(), String.format("wrong sessionId for order %s", orderId));
        assertEquals(expected, order.get().getStatus(), String.format("wrong order status for order %s", orderId));
    }

    /**
     * get all session ids for which a reservation exists.
     * 
     * @return set session ids
     */
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
     * extract session id from json representation of saga details
     * 
     * @param json saga details as json
     * @return the session id
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
     * extract order id from json representation of saga details
     * 
     * @param json saga details as json
     * @return the order id
     */
    private String getOrderId(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return mapper.treeToValue(root.path("orderId"), String.class);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block

            throw new RuntimeException("parsing json failed", e);
        }
    }
}