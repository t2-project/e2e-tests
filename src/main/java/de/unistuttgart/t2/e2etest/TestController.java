package de.unistuttgart.t2.e2etest;

import de.unistuttgart.t2.common.SagaRequest;
import de.unistuttgart.t2.e2etest.exception.FakeFailureException;
import de.unistuttgart.t2.order.repository.OrderStatus;
import de.unistuttgart.t2.payment.PaymentData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Random;

import static de.unistuttgart.t2.e2etest.TestService.*;

/**
 * Defines endpoints for the e2e test.
 * <p>
 * The e2e test intercepts the T2-Project at two points. First it takes the {@link de.unistuttgart.t2.common.SagaRequest
 * SagaRequest} from the UIBackend to the orchestrator, and then it takes the requests that the payment service posts.
 * This way the e2e test knows what reply the payment service received and thus also knows whether the saga instance is
 * supposed to succeed or not.
 *
 * @author maumau
 */
@RestController
public class TestController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    private TestService service;

    /**
     * Intercepts the communication from the UIBackend to the orchestrator.
     * <p>
     * Takes the {@code request} that from UIBackend and modifies it such that it might be correlated to an incoming
     * payment request. Also sets what reply said payment request will receive and forward finally forwards the request
     * to the orchestrator.
     * <p>
     * As only the information about the credit cart will end up in the request to the payment provider, the field
     * {@code checksum} will be miss used to correlate the payment requests received in
     * {@linkplain TestController#fakepay(PaymentData)} to the saga request received in this operation.
     * <p>
     * It would have been pretty cool, if i could just put the saga id into the request but i cannot because i only get
     * the saga id after i post the request to the orchestrator.
     *
     * @param request intended for orchestrator
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(value = "/test")
    public void test(@RequestBody SagaRequest request) {
        LOG.info("incoming saga request: {}", request.toString());

        // 'miss use' sessionId for correlation.
        String correlationId = request.getSessionId();

        if(correlationToSaga.containsKey(correlationId)) {
            throw new HttpServerErrorException(HttpStatus.NOT_IMPLEMENTED,
                "E2E-Test does not support concurrent saga requests with the same session id!");
        }

        if (new Random().nextDouble() < 0.5) {
            correlationToStatus.put(correlationId, OrderStatus.FAILURE);
        } else {
            correlationToStatus.put(correlationId, OrderStatus.SUCCESS);
        }

        // sessionId now _twice_ in the saga request but only the one saved as
        // 'checksum' will find its way back to this service.
        SagaRequest modifiedRequest = new SagaRequest(request.getSessionId(), request.getCardNumber(),
            request.getCardOwner(), request.getSessionId(), request.getTotal());

        String sagaID = service.postToOrchestrator(modifiedRequest);

        // ... but also save sagaId because this is the only time i can get it!!
        correlationToSaga.put(correlationId, sagaID);
    }

    /**
     * Fakes the reply to the payment service and start a thread that asserts the saga instance.
     * <p>
     * The payment service request payment for each saga instance. If that request fails, the service tries again. This
     * endpoints replies are consistent for a request and its retries.
     * <p>
     * Also the assertion thing is triggered only once for each saga instance. There is no need to check the same thing
     * twice.
     * <p>
     * As described in {@link TestController#test(SagaRequest)}, the field {@code checksum} hold an id to correlate the
     * payment request to the saga instance.
     *
     * @param paymentdata request data. the {@code checksum} is the with correlation id
     * @throws Exception if payment 'failed'
     */
    @PostMapping(value = "/fakepay")
    public void fakepay(@RequestBody PaymentData paymentdata) throws Exception {
        LOG.info("incoming payment request for sagaid {}", paymentdata.getChecksum());

        // i have never seen this id in my life before.
        if (!correlationToStatus.containsKey(paymentdata.getChecksum())) {
            if (new Random().nextDouble() < 0.5) {
                return;
            }
            throw new FakeFailureException("fake failure with correlation");
        }

        // uh, i know that one but it's still the first time i see it!
        if (!inprogress.contains(paymentdata.getChecksum())) {
            String sagaid = correlationToSaga.get(paymentdata.getChecksum());
            LOG.info("Assert for: correlationsid: {}, sagaid: {}", paymentdata.getChecksum(),
                sagaid);

            new Thread(() -> service.sagaRuntimeTest(paymentdata.getChecksum()), sagaid).start();
            inprogress.add(paymentdata.getChecksum());
        }

        OrderStatus expected = correlationToStatus.get(paymentdata.getChecksum());

        if (expected == OrderStatus.FAILURE) {
            throw new FakeFailureException("fake failure that will be asserted");
        }
    }

    /**
     * Creates the response entity in case of faked failure.
     *
     * @param exception the exception that was thrown
     * @return a response entity with an exceptional message
     */
    @ExceptionHandler(FakeFailureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleFakeFailureException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }
}
