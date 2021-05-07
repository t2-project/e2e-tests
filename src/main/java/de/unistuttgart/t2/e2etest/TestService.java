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
import java.util.concurrent.TimeoutException;

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
 * service for testing the t2 store.
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

    @Autowired
    RestTemplate template;
    @Autowired
    SagaInstanceRepository sagaRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    ProductRepository productRepository;

    public Set<String> inprogress = new HashSet();

    // i'm not sure how concurrent spring stuff is, so i'm using a concurrent map,
    // feels saver or something like that.
    public Map<String, OrderStatus> correlationToStatus = new ConcurrentHashMap<>();
    public Map<String, String> correlationToSaga = new ConcurrentHashMap<>();

    /**
     * test sagas at runtime
     * 
     * @param request the saga request
     */
    public void sagaRuntimeTest(String correlationid) {
        String sagaid = correlationToSaga.get(correlationid);
        LOG.info(String.format("%s : start test", sagaid));
        try {
            SagaInstance sagainstance = getFinishedSagaInstance(sagaid);

            assertOrderStatus(sagainstance, correlationToStatus.get(correlationid));
            assertReservationStatus(sagainstance, correlationToStatus.get(correlationid));
            assertSagaInstanceStatus(sagainstance, correlationToStatus.get(correlationid));

            LOG.info(String.format("%s : no failure", sagaid));
        } catch (Throwable e) {
            LOG.info(String.format("%s : has failure : %s ", sagaid, e.getMessage()));
            e.printStackTrace();
        }
        correlationToSaga.remove(correlationid);
        correlationToStatus.remove(correlationid);
        inprogress.remove(correlationid);
    }

    /**
     * send post request to start a saga to the orchestrator
     * 
     * @param request the request to send
     * @return id of started saga instance
     */
    public String postToOrchestrator(SagaRequest request) {
        return template.postForEntity(orchestrator, request, String.class).getBody();
    }

    /**
     * get saga instance with given id but only if its finished.
     * 
     * poll saga instance database repeatedly until either the saga instance
     * transitioned into end state or a maximum number of iterations is exceeded.
     * 
     * @param sagaid
     * @return saga instance in end state
     * @throws TimeoutException if max number of iterations is exceeded.
     */
    private SagaInstance getFinishedSagaInstance(String sagaid) throws TimeoutException {
        String sagatype = "de.unistuttgart.t2.orchestrator.saga.Saga";

        // TODO : might be usefull if maxiteration and seconds are configurable.
        int maxiteration = 20;
        int seconds = 5000;

        for (int i = 0; i < maxiteration; i++) {
            SagaInstance actual = sagaRepository.find(sagatype, sagaid);

            // isEndState() always returns 'false' (even though the saga instance has very
            // clearly reached the endstate) thus test with stateName.
            if (isEndState(actual.getStateName())) {
                return actual;
            }
            try {
                Thread.sleep(seconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        throw new TimeoutException(
                String.format("Saga instance %s did not finish after %d  iteration and a total of %d seconds.", sagaid,
                        maxiteration, maxiteration * seconds));
    }

    /**
     * assert status of saga instance.
     * 
     * @note apparently the 'compensation' entry from the saga instance database
     *       represents whether the instance is current instance is compensating,
     *       i.e. a finished saga instance is never compensating
     * 
     * @param sagainstance the saga instance under test
     * @param expected     outcome to test for
     */
    private void assertSagaInstanceStatus(SagaInstance sagainstance, OrderStatus expected) {
        // end state already reached, or else we would not be here.
//        if (expected == OrderStatus.FAILURE) {
//            assertTrue(sagainstance.isCompensating());
//        } else {
//            assertFalse(sagainstance.isCompensating());
//        }
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
     * @return true is saga instance in endstate
     */
    private boolean isEndState(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return mapper.treeToValue(root.path("endState"), String.class).equals("true");
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException("parsing json failed", e);
        }
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