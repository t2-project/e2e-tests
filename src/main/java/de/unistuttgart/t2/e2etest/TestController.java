package de.unistuttgart.t2.e2etest;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.unistuttgart.t2.common.saga.SagaData;
import de.unistuttgart.t2.common.saga.SagaRequest;
import de.unistuttgart.t2.payment.provider.PaymentData;
import de.unistuttgart.t2.repository.OrderStatus;

@RestController
public class TestController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    TestService service;

    /**
     * we intercept ui backend / orchestrator.
     * 
     * ui backend supposed to call this endpoint.
     * 
     * 
     * 
     * @param sagaid
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(value = "/test")
    public void test(@RequestBody SagaRequest request) {
        LOG.info(String.format("incoming saga request: %s", request.toString()));

        
        String correlationId = request.getSessionId();
        
        TestService.correlationToStatus.put(correlationId, OrderStatus.FAILURE);
        
        request.setChecksum(request.getSessionId());
        String sagaID = service.postToOrchestrator(request);
        
        TestService.correlationToSaga.put(correlationId, sagaID);
    }

    /** 
     * fake replay to payment.
     * 
     * @param sagaid
     * @throws Exception
     */
    @PostMapping(value = "/fakepay")
    public void fakepay(@RequestBody PaymentData paymentdata) throws Exception {
        LOG.info(String.format("payment saga request:"));
        
        String sagaid = TestService.correlationToSaga.get(paymentdata.getChecksum());
        
        new Thread(()->service.executethings(()-> service.sagaRuntimeTest(paymentdata.getChecksum()), sagaid), sagaid).start();
        
        LOG.info("sent reply");        
 
        OrderStatus expected = TestService.correlationToStatus.get(paymentdata.getChecksum());
        if (expected == OrderStatus.FAILURE) {
            throw new Exception();
        } 
                
    }
}