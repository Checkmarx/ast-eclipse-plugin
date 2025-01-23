package checkmarx.ast.eclipse.plugin.tests.integration;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public class BaseIntegrationTest {
    
    protected static final Logger log = LoggerFactory.getLogger(BaseIntegrationTest.class);
    protected CxWrapper wrapper;
    
    @Before
    public void setUp() throws Exception {
        CxConfig config = CxConfig.builder()
            .apiKey(Environment.API_KEY)
            .build();
            
        wrapper = new CxWrapper(config, log);
    }
    
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Ignore
        }
    }
} 