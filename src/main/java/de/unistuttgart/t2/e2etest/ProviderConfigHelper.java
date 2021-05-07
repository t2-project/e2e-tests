package de.unistuttgart.t2.e2etest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import de.unistuttgart.t2.repository.OrderStatus;

/**
 * to configure the how the payment provider is supposed to reply.
 * @author maumau
 *
 */
@Component
public class ProviderConfigHelper {
    
    
    private OrderStatus status;
    
    
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    RestTemplate template;
    
    @Value("${t2.e2etest.failurerate.url}")
    String failurerate;
    
    @Value("${t2.e2etest.timeoutrate.url}")
    String timeoutrate;
    
    @Value("${t2.e2etest.timeout.url}")
    String timeout;
    
    @PostConstruct
    public void init() {
        setUpSuccess();
        status = OrderStatus.SUCCESS;
    }
    
    
    public OrderStatus getStatus() {
        return status;
    }


    /**
     * configure credit institute, such that payment step of saga succeeds
     */
    public void setUpSuccess() {
        assertEquals(0, template.getForEntity(timeoutrate + "0", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(0, template.getForEntity(failurerate + "0", Double.class).getBody(), "failed to set failurerate");
        status = OrderStatus.SUCCESS;
    }

    /**
     * configure credit institute such that payment step of saga fails
     */
    public void setUpFailure() {
        assertEquals(0, template.getForEntity(timeoutrate + "0", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(1, template.getForEntity(failurerate + "1", Double.class).getBody(), "failed to set failurerate");
        status = OrderStatus.FAILURE;
    }

    /**
     * configure credit institute such that payment step of saga timeouts
     */
    public void setUpTimeout() {
        assertEquals(1, template.getForEntity(timeoutrate + "1", Double.class).getBody(), "failed to set timeoutrate");
        assertEquals(0, template.getForEntity(failurerate + "0", Double.class).getBody(), "failed to set failurerate");
        assertEquals(4000, template.getForEntity(timeout + "4000", Double.class).getBody(), "failed to set delay");
        status = OrderStatus.FAILURE;
    }
}
