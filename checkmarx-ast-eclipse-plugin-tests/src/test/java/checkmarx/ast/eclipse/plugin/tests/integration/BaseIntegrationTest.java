package checkmarx.ast.eclipse.plugin.tests.integration;

import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.eclipse.utils.PluginConstants;
import checkmarx.ast.eclipse.plugin.tests.common.Environment;

public abstract class BaseIntegrationTest {
    
    protected static final String VALID_SCAN_ID = Environment.SCAN_ID;
    protected static final String INVALID_SCAN_ID = "invalid-scan-id";
    protected static final String VALID_API_KEY = Environment.API_KEY;
    
    @Mock
    protected Logger mockLogger;
    
    protected CxWrapper cxWrapper;
    protected static boolean initialized = false;
    
    @BeforeClass
    public static void setUpClass() {
        // Global test setup if needed
    }
    
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        if (!initialized) {
            initializeCxWrapper();
            initialized = true;
        }
    }
    
    protected void initializeCxWrapper() throws Exception {
        CxConfig config = CxConfig.builder()
                .apiKey(VALID_API_KEY)
                .build();
        cxWrapper = new CxWrapper(config, mockLogger);
    }
    
    protected void reinitializeCxWrapper(String apiKey) throws Exception {
        CxConfig config = CxConfig.builder()
                .apiKey(apiKey)
                .build();
        cxWrapper = new CxWrapper(config, mockLogger);
    }
}