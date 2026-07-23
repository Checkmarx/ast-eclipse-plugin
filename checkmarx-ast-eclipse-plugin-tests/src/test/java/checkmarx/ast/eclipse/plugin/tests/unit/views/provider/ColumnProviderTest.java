package checkmarx.ast.eclipse.plugin.tests.unit.views.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.swt.graphics.Image;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.provider.ColumnProvider;

class ColumnProviderTest {

    @Test
    void testGetText_nameFunction_returnsName() {
        ColumnProvider provider = new ColumnProvider(m -> null, DisplayModel::getName);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("col-name").build();
        assertEquals("col-name", provider.getText(model));
    }

    @Test
    void testGetText_severityFunction_returnsSeverity() {
        ColumnProvider provider = new ColumnProvider(m -> null, DisplayModel::getSeverity);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        model.setSeverity("CRITICAL");
        assertEquals("CRITICAL", provider.getText(model));
    }

    @Test
    void testGetText_functionReturnsNull_propagatesNull() {
        ColumnProvider provider = new ColumnProvider(m -> null, m -> null);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        assertNull(provider.getText(model));
    }

    @Test
    void testGetImage_returnsImageFromFunction() {
        Image mockImage = mock(Image.class);
        ColumnProvider provider = new ColumnProvider(m -> mockImage, DisplayModel::getName);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        assertSame(mockImage, provider.getImage(model));
    }

    @Test
    void testGetImage_functionReturnsNull_propagatesNull() {
        ColumnProvider provider = new ColumnProvider(m -> null, DisplayModel::getName);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        assertNull(provider.getImage(model));
    }

    @Test
    void testGetText_andGetImage_useIndependentFunctions() {
        Image mockImage = mock(Image.class);
        ColumnProvider provider = new ColumnProvider(
                m -> mockImage,
                m -> m.getSeverity() + ":" + m.getName());
        DisplayModel model = new DisplayModel.DisplayModelBuilder("vuln").build();
        model.setSeverity("LOW");

        assertEquals("LOW:vuln", provider.getText(model));
        assertSame(mockImage, provider.getImage(model));
    }
}
