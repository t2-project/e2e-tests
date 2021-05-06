package de.unistuttgart.t2.e2etest;

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

@RestController
public class TestController {

	private final Logger LOG = LoggerFactory.getLogger(getClass());
	
	@Autowired 
	SagaIntegrationTest service;

	@ResponseStatus(HttpStatus.ACCEPTED)
	@PostMapping(value = "/test")
	public void test(@RequestBody String sagaid) {
	    LOG.info(String.format("incoming saga:", sagaid));
		
	    service.executethings(() -> service.sagaRuntimeTest(sagaid), sagaid);
	    
	}
}