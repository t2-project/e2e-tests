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
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unistuttgart.t2.common.SagaRequest;
import de.unistuttgart.t2.inventory.repository.InventoryItem;
import de.unistuttgart.t2.inventory.repository.ProductRepository;
import de.unistuttgart.t2.order.repository.OrderItem;
import de.unistuttgart.t2.order.repository.OrderRepository;
import de.unistuttgart.t2.order.repository.OrderStatus;
import io.eventuate.tram.sagas.orchestration.SagaInstance;
import io.eventuate.tram.sagas.orchestration.SagaInstanceRepository;

/**
 * 
 * Responsible for asserting that the T2 store's state is in the end always
 * correct.
 * 
 * @author maumau
 *
 */
@Component
public class TestService {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    @Value("${t2.e2etest.orchestrator.url}")
    private String orchestrator;

    @Autowired
    private RestTemplate template;
    @Autowired
    private SagaInstanceRepository sagaRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;

    public Set<String> inprogress = new HashSet<String>();

    // i'm not sure how concurrent spring stuff is, so i'm using a concurrent map,
    // feels saver or something like that.always
    public Map<String, OrderStatus> correlationToStatus = new ConcurrentHashMap<>();
    public Map<String, String> correlationToSaga = new ConcurrentHashMap<>();

    /**
     * Tests the T2 stores state at runtime.
     * 
     * @param correlationid to identify the saga instance
     */
    public void sagaRuntimeTest(String correlationid) {
        String sagaid = correlationToSaga.get(correlationid);
        LOG.info(String.format("%s : start test", sagaid));
        try {
            SagaInstance sagainstance = getFinishedSagaInstance(sagaid);
            
            String sessionId = getSessionId(sagainstance.getSerializedSagaData().getSagaDataJSON());
            String orderId = getOrderId(sagainstance.getSerializedSagaData().getSagaDataJSON());

            assertOrderStatus(orderId, sessionId, correlationToStatus.get(correlationid));
            assertReservationStatus(sessionId);
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
     * Post saga request to the orchestrator.
     * 
     * @param request the request to send
     * @return id of started saga instance
     */
    public String postToOrchestrator(SagaRequest request) {
        return template.postForEntity(orchestrator, request, String.class).getBody();
    }

    /**
     * Get saga instance with the given id but only if it is finished.
     * 
     * <p>
     * Poll the saga instance database repeatedly until either the saga instance
     * transitioned into end state or a maximum number of iterations is exceeded.
     * 
     * @param sagaid to find the saga instance
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
     * Asserts the state of the saga instance.
     * 
     * <p>
     * Apparently the 'compensation' entry from the saga instance database
     * represents whether the current instance is compensating, i.e. a finished saga
     * instance is never compensating.
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
     * Assert the absence of reservations belonging to the given saga instance.
     * 
     * <p>
     * For a successful saga instance as well as for a saga instance that was rolled
     * back, there should be no reservations.
     * 
     * @param sessionId id of session
     */
    private void assertReservationStatus(String sessionId) {
        Set<String> sessionIds = getSessionIdsFromReservations();

        assertFalse(sessionIds.contains(sessionId),
                String.format("reservations for sessionId %s not deleted.", sessionId));

        // i will not assert, that the product unis were updated (or not) because that
        // is inventory internal and thus not part of e2e.
    }

    /**
     * Assert correct state of the order belonging to the given saga instance.
     * 
     * <p>
     * If the saga rolled back, the order status should be
     * {@link OrderStatus#FAILURE FAILURE} otherwise it should be
     * {@link OrderStatus#SUCCESS SUCCESS}.
     * 
     * @param orderId   id of order
     * @param sessionId id of session
     * @param expected  outcome to be expected
     */
    private void assertOrderStatus(String orderId, String sessionId, OrderStatus expected) {

        Optional<OrderItem> order = orderRepository.findById(orderId);
        assertTrue(order.isPresent(), String.format("missing order with orderid %s", orderId));
        assertEquals(sessionId, order.get().getSessionId(), String.format("wrong sessionId for order %s", orderId));
        assertEquals(expected, order.get().getStatus(), String.format("wrong order status for order %s", orderId));
    }

    /**
     * Get all sessionIds for which a reservation exists.
     * 
     * @return A set of sessionIds
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
     * Figures out whether the given saga instance is finished.
     * 
     * <p>
     * Extracts the 'endState' field from the JSON representation of the saga State
     * and checks whether it is {@code true} or {@code false}.
     * 
     * <p>
     * There exists the query {@link SagaInstance#isEndState()} but it kind of
     * always returns 'false'.
     * 
     * @param json state of saga instance as JSON
     * @return true iff the saga instance is finished, false otherwise
     */
    private boolean isEndState(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            return mapper.treeToValue(root.path("endState"), String.class).equals("true");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Extract the sessionId from the saga instance's data.
     * 
     * <p>
     * The data is a JSON representation of
     * {@link de.unistuttgart.t2.common.saga.SagaData SagaData}.
     * 
     * @param json saga data as json
     * @return the sessionId
     * @throws JsonProcessingException  if something was wrong with the JSON
     * @throws JsonMappingException     if something was wrong with the JSON
     */
    private String getSessionId(String json) throws JsonMappingException, JsonProcessingException {
       
        JsonNode root = mapper.readTree(json);
        return mapper.treeToValue(root.path("sessionId"), String.class);

    }

    /**
     * Extract the orderId from the saga instance's data.
     * 
     * <p>
     * The data is a JSON representation of
     * {@link de.unistuttgart.t2.common.saga.SagaData SagaData}.
     * 
     * @param json saga details as json
     * @return the orderId
     * @throws JsonProcessingException  if something was wrong with the JSON
     * @throws JsonMappingException     if something was wrong with the JSON
     */
    private String getOrderId(String json) throws JsonMappingException, JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        return mapper.treeToValue(root.path("orderId"), String.class);
    }
}