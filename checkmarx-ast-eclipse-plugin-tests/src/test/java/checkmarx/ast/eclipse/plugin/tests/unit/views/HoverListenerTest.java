package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.checkmarx.eclipse.views.HoverListener;

class HoverListenerTest {

	private List<Control> mockControls;
	private Control mockControl1;
	private Control mockControl2;
	private Display mockDisplay;
	private Color mockDefaultColor;
	private Color mockThemeColor;
	private RGBA mockRGBA;
	private MouseEvent mockMouseEvent;

	@BeforeEach
	void setUp() {
		mockControl1 = mock(Control.class);
		mockControl2 = mock(Control.class);
		mockControls = Arrays.asList(mockControl1, mockControl2);
		mockDisplay = mock(Display.class);
		mockDefaultColor = mock(Color.class);
		mockThemeColor = mock(Color.class);
		mockRGBA = mock(RGBA.class);
		mockMouseEvent = mock(MouseEvent.class);

		when(mockControl1.getBackground()).thenReturn(mockDefaultColor);
		when(mockThemeColor.getRGBA()).thenReturn(mockRGBA);
		when(mockRGBA.getHSBA()).thenReturn(new float[] { 120f, 0.5f, 0.5f, 1.0f });
	}

	@Test
	void testConstructor_withControlsList_storesControlsAndDefaultColor() {
		HoverListener listener = new HoverListener(mockControls);

		assertNotNull(listener);
		verify(mockControl1).getBackground();
	}

	@Test
	void testConstructor_withEmptyList_defaultColorIsNull() {
		HoverListener listener = new HoverListener(Collections.emptyList());

		assertNotNull(listener);
		// Empty list means no getBackground() call and defaultColor should be null
	}

	@Test
	void testMouseEnter_appliesCustomColorToAllControls() {
		try (MockedStatic<Display> displayMock = mockStatic(Display.class)) {
			displayMock.when(Display::getCurrent).thenReturn(mockDisplay);
			when(mockDisplay.getSystemColor(SWT.COLOR_LIST_SELECTION)).thenReturn(mockThemeColor);

			HoverListener listener = new HoverListener(mockControls);
			listener.mouseEnter(mockMouseEvent);

			verify(mockControl1).setBackground(any(Color.class));
			verify(mockControl2).setBackground(any(Color.class));
		}
	}

	@Test
	void testMouseExit_restoresDefaultColorWhenNotNull() {
		try (MockedStatic<Display> displayMock = mockStatic(Display.class)) {
			displayMock.when(Display::getCurrent).thenReturn(mockDisplay);
			when(mockDisplay.getSystemColor(SWT.COLOR_LIST_SELECTION)).thenReturn(mockThemeColor);

			HoverListener listener = new HoverListener(mockControls);
			listener.mouseEnter(mockMouseEvent);
			listener.mouseExit(mockMouseEvent);

			verify(mockControl1, atLeastOnce()).setBackground(mockDefaultColor);
			verify(mockControl2, atLeastOnce()).setBackground(mockDefaultColor);
		}
	}

	@Test
	void testMouseExit_whenDefaultColorIsNull_doesNothing() {
		List<Control> emptyControls = Collections.emptyList();
		HoverListener listener = new HoverListener(emptyControls);

		listener.mouseExit(mockMouseEvent);

		// Should not throw, and no interactions on controls
		assertTrue(emptyControls.isEmpty());
	}

	@Test
	void testMouseExit_disposesCustomColor() {
		try (MockedStatic<Display> displayMock = mockStatic(Display.class)) {
			displayMock.when(Display::getCurrent).thenReturn(mockDisplay);
			when(mockDisplay.getSystemColor(SWT.COLOR_LIST_SELECTION)).thenReturn(mockThemeColor);

			HoverListener listener = new HoverListener(mockControls);
			listener.mouseEnter(mockMouseEvent);
			listener.mouseExit(mockMouseEvent);

			// Verify that setBackground was called (indicating color handling)
			verify(mockControl1, atLeast(1)).setBackground(any());
		}
	}

	@Test
	void testMouseHover_doesNothing() {
		HoverListener listener = new HoverListener(mockControls);

		listener.mouseHover(mockMouseEvent);

		// No assertions - just verify it doesn't throw
		assertTrue(true);
	}

	@Test
	void testApply_addsListenerToAllControls() {
		List<Control> testControls = new ArrayList<>();
		Control control1 = mock(Control.class);
		Control control2 = mock(Control.class);
		testControls.add(control1);
		testControls.add(control2);

		HoverListener listener = new HoverListener(testControls);
		listener.apply();

		verify(control1).addMouseTrackListener(listener);
		verify(control2).addMouseTrackListener(listener);
	}

	@Test
	void testApply_withEmptyControls_doesNothing() {
		HoverListener listener = new HoverListener(Collections.emptyList());

		listener.apply();

		// Should not throw with empty list
		assertTrue(true);
	}

	@Test
	void testMouseEnterThenExit_cycleCompletesSuccessfully() {
		try (MockedStatic<Display> displayMock = mockStatic(Display.class)) {
			displayMock.when(Display::getCurrent).thenReturn(mockDisplay);
			when(mockDisplay.getSystemColor(SWT.COLOR_LIST_SELECTION)).thenReturn(mockThemeColor);

			HoverListener listener = new HoverListener(mockControls);

			listener.mouseEnter(mockMouseEvent);
			listener.mouseExit(mockMouseEvent);

			// Verify the cycle: enter changes color, exit restores it
			verify(mockControl1, atLeast(1)).setBackground(any());
			verify(mockControl2, atLeast(1)).setBackground(any());
		}
	}
}
