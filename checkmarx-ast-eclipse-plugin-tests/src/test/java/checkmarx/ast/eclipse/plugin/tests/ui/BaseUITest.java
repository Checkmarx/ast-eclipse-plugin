package checkmarx.ast.eclipse.plugin.tests.ui;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class BaseUITest {
	
	protected static final String TAB_WINDOW = "Window";
	
	protected static final String ITEM_SHOW_VIEW = "Show View";
	protected static final String ITEM_PREFERENCES = "Preferences";
	protected static final String ITEM_OTHER = "Other...";
	protected static final String ITEM_CHECKMARX = "Checkmarx";
	protected static final String ITEM_CHECKMARX_AST = "Checkmarx AST";
	protected static final String ITEM_CHECKMARX_AST_SCAN = "Checkmarx AST Scan";
	
	protected static final String LABEL_SERVER_URL = "Server Url:";
	protected static final String LABEL_TENANT = "Tenant:";
	protected static final String LABEL_AST_API_KEY = "AST API Key:";
	protected static final String LABEL_SCAN_ID = "Scan Id:";
	
	protected static final String COLUMN_TITLE = "Title";

	protected static final String BTN_OPEN = "Open";
	protected static final String BTN_APPLY = "Apply";
	protected static final String BTN_TEST_CONNECTION = "Test Connection";
	protected static final String BTN_OK = "OK";
	protected static final String BTN_APPLY_AND_CLOSE = "Apply and Close";
	
	protected static final String SHELL_AUTHENTICATION = "Authentication";
	
	protected static final String VIEW_CHECKMARX_AST_SCAN = "Checkmarx AST Scan";
	
	protected static SWTWorkbenchBot _bot;
	
	@BeforeClass
	public static void beforeClass() throws Exception {

		// Used to decrease tests velocity
		SWTBotPreferences.PLAYBACK_DELAY = 100;

		_bot = new SWTWorkbenchBot();
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@AfterClass
	public static void sleep() {
		_bot.sleep(2000);
	}
	
	
	protected static void sleep(long millis) {
		_bot.sleep(millis);
	}
}
