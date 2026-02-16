package checkmarx.ast.eclipse.plugin.tests.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
// DELETE these Mockito imports - not needed
// import org.mockito.Mock;
// import org.mockito.Mockito;
// import org.mockito.MockitoAnnotations;

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

    // DELETE @Mock - not initialized
    // protected Logger mockLogger;

    protected Authenticator authenticator;
    protected CxWrapper cxWrapper;
    protected static boolean initialized = false;

    @BeforeEach
    void setUp() {
        // Nothing needed
    }

    @AfterEach
    void tearDown() {
        // Nothing needed
    }
    
    protected void initializeCxWrapper() throws Exception {
        logger.info("Initializing CxWrapper");
        CxConfig config = CxConfig.builder()
                .apiKey(VALID_API_KEY)
                .build();
        // FIX: Use real logger, not mock
        cxWrapper = new CxWrapper(config, logger);  // ← Changed mockLogger to logger
        logger.info("CxWrapper initialized successfully");
    }
    
    protected void reinitializeCxWrapper(String apiKey) throws Exception {
        logger.info("Reinitializing CxWrapper with new API key");
        CxConfig config = CxConfig.builder()
                .apiKey(apiKey)
                .build();
        // FIX: Use real logger
        cxWrapper = new CxWrapper(config, logger);  // ← Changed mockLogger to logger
        logger.info("CxWrapper reinitialized successfully");
    }
}
