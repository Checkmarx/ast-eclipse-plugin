package checkmarx.ast.eclipse.plugin.tests.unit.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.utils.NotificationPopUpUI;

public class NotificationPopUpUITest {

	private Display mockDisplay;
	private SelectionAdapter mockTextAction;
	private SelectionAdapter mockBtnAction;
	private String testTitle;
	private String testText;
	private String testBtnText;

	@BeforeEach
	void setUp() {
		mockDisplay = mock(Display.class);
		mockTextAction = mock(SelectionAdapter.class);
		mockBtnAction = mock(SelectionAdapter.class);
		testTitle = "Test Notification";
		testText = "This is a test notification message";
		testBtnText = "Click Me";
	}

	@Test
	void testConstructor_withAllParameters() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, testText, mockTextAction, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withNullTextAction() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, testText, null, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withNullBtnAction() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, testText, mockTextAction, testBtnText, null);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withAllNullActions() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, testText, null, testBtnText, null);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withNullTitle() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, null, testText, mockTextAction, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withNullText() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, null, mockTextAction, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withNullBtnText() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, testText, mockTextAction, null, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withEmptyTitle() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, "", testText, mockTextAction, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}

	@Test
	void testConstructor_withEmptyText() {
		NotificationPopUpUI popup = new NotificationPopUpUI(mockDisplay, testTitle, "", mockTextAction, testBtnText, mockBtnAction);

		assertNotNull(popup);
	}
}
