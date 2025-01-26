package checkmarx.ast.eclipse.plugin.tests.integration;

import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.runner.Authenticator;

import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public abstract class BaseIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);
    
    protected static final String VALID_SCAN_ID = Environment.SCAN_ID;
    protected static final String INVALID_SCAN_ID = "invalid-scan-id";
    protected static final String VALID_API_KEY = Environment.API_KEY;
    
    @Mock
    protected Logger mockLogger;
    
    protected Authenticator authenticator;
    protected CxWrapper cxWrapper;
    protected static boolean initialized = false;
    
    @Before
    public void setUp() throws Exception {
        logger.info("Setting up test: {}", this.getClass().getSimpleName());
        logger.info("API Key available: {}", VALID_API_KEY != null);
        MockitoAnnotations.initMocks(this);
        authenticator = new Authenticator(mockLogger);

        if (!initialized) {
            initializeCxWrapper();
            initialized = true;
        }
    }
    
    protected void initializeCxWrapper() throws Exception {
        logger.info("Initializing CxWrapper");
        CxConfig config = CxConfig.builder()
                .apiKey(VALID_API_KEY)
                .build();
        cxWrapper = new CxWrapper(config, mockLogger);
        logger.info("CxWrapper initialized successfully");
    }
    
    protected void reinitializeCxWrapper(String apiKey) throws Exception {
        logger.info("Reinitializing CxWrapper with new API key");
        CxConfig config = CxConfig.builder()
                .apiKey(apiKey)
                .build();
        cxWrapper = new CxWrapper(config, mockLogger);
        logger.info("CxWrapper reinitialized successfully");
    }
}