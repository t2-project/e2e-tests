package de.unistuttgart.t2.e2etest;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.unistuttgart.t2.common.saga.SagaRequest;
import de.unistuttgart.t2.payment.provider.PaymentData;
import de.unistuttgart.t2.repository.OrderStatus;

@RestController
public class TestController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    TestService service;

    /**
     * intercept the communication from ui backend to orchestrator.
     * 
     * takes sagaRequest from ui backend and prepare it such that it may be
     * correlated to an incoming payment request. also set what reply said payment
     * request will receive and forward finally forwart the request to orchestrator.
     * 
     * miss uses checksum field in request to pass the correlation id on.
     * 
     * @param requeset intended for orchestrator
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(value = "/test")
    public void test(@RequestBody SagaRequest request) {
        LOG.info(String.format("incoming saga request: %s", request.toString()));

        // 'miss use' sessionId for correlation. it's unique anyway...
        String correlationId = request.getSessionId();

        if (new Random().nextDouble() < 0.5) {
            TestService.correlationToStatus.put(correlationId, OrderStatus.FAILURE);
        } else {
            TestService.correlationToStatus.put(correlationId, OrderStatus.SUCCESS);
        }

        request.setChecksum(request.getSessionId());
        String sagaID = service.postToOrchestrator(request);

        // ... but also save sagaId because this is the only play i can get it!!
        TestService.correlationToSaga.put(correlationId, sagaID);
    }

    /**
     * fake reply to payment and start test for saga instance
     * 
     * @param paymentdata request data with correlation id as checksum.
     * @throws Exception if payment 'failed'
     */
    @PostMapping(value = "/fakepay")
    public void fakepay(@RequestBody PaymentData paymentdata) throws Exception {

        String sagaid = TestService.correlationToSaga.get(paymentdata.getChecksum());

        new Thread(() -> service.sagaRuntimeTest(paymentdata.getChecksum()), sagaid).start();

        OrderStatus expected = TestService.correlationToStatus.get(paymentdata.getChecksum());

        if (expected == OrderStatus.FAILURE) {
            throw new Exception("fake pay failure");
        }

    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleFakePayException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }
}