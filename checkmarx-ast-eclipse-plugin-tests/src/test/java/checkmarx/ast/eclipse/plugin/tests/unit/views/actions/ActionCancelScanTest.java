package checkmarx.ast.eclipse.plugin.tests.unit.views.actions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.actions.ActionCancelScan;
import com.checkmarx.eclipse.views.actions.ActionStartScan;

class ActionCancelScanTest {

    private static MockedStatic<Activator> activatorMock;

    @BeforeAll
    static void setUpClass() {
        activatorMock = Mockito.mockStatic(Activator.class);
        ImageDescriptor descriptor = mock(ImageDescriptor.class);
        Image image = mock(Image.class);
        when(descriptor.createImage()).thenReturn(image);
        activatorMock.when(() -> Activator.getImageDescriptor(anyString())).thenReturn(descriptor);
    }

    @AfterAll
    static void tearDownClass() {
        activatorMock.close();
    }

    @Test
    void testCancelScanAction_run_callsOnCancelAndDisablesAction() {
        DisplayModel rootModel = mock(DisplayModel.class);
        TreeViewer resultsTree = mock(TreeViewer.class);

        Action action = new ActionCancelScan(rootModel, resultsTree).createAction();
        action.setEnabled(true);

        try (MockedStatic<ActionStartScan> asMock = Mockito.mockStatic(ActionStartScan.class)) {
            asMock.when(ActionStartScan::onCancel).thenAnswer(invocation -> null);

            action.run();

            asMock.verify(ActionStartScan::onCancel);
            assertFalse(action.isEnabled(), "action should be disabled after run()");
        }
    }
}
