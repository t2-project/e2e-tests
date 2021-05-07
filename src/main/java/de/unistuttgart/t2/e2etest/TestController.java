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
import de.unistuttgart.t2.repository.OrderStatus;

@RestController
public class TestController {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    SagaIntegrationTest service;

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
       
        service.executethings(() -> service.sagaRuntimeTest(request), request.getSessionId());

    }

    /** 
     * fake replay to payment.
     * 
     * @param sagaid
     * @throws Exception
     */
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(value = "/fakepay")
    public void fakepay(@RequestBody String sagaid) throws Exception {
        LOG.info(String.format("payment saga request: %s", sagaid));
        
        if (new Random().nextDouble() < 0.5) {
            SagaIntegrationTest.replyForSaga.put(sagaid, OrderStatus.FAILURE);
            throw new Exception();
        } else {
            SagaIntegrationTest.replyForSaga.put(sagaid, OrderStatus.SUCCESS);
        }
    }
}