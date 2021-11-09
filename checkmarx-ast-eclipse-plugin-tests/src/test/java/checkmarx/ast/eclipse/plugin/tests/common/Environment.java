package checkmarx.ast.eclipse.plugin.tests.common;

public class Environment {

	public static final String BASE_URL = System.getenv("CX_BASE_URI");
    public static final String TENANT = System.getenv("CX_TENANT");
    public static final String API_KEY = System.getenv("CX_APIKEY");
    public static final String SCAN_ID = System.getenv("CX_TEST_SCAN");
}
