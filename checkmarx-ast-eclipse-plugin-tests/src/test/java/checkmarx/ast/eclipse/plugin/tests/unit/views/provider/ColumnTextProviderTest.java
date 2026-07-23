package checkmarx.ast.eclipse.plugin.tests.unit.views.provider;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.provider.ColumnTextProvider;

class ColumnTextProviderTest {

    @Test
    void testGetText_nameFunction_returnsName() {
        ColumnTextProvider provider = new ColumnTextProvider(DisplayModel::getName);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("my-name").build();
        assertEquals("my-name", provider.getText(model));
    }

    @Test
    void testGetText_severityFunction_returnsSeverity() {
        ColumnTextProvider provider = new ColumnTextProvider(DisplayModel::getSeverity);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        model.setSeverity("HIGH");
        assertEquals("HIGH", provider.getText(model));
    }

    @Test
    void testGetText_queryNameFunction_returnsQueryName() {
        ColumnTextProvider provider = new ColumnTextProvider(DisplayModel::getQueryName);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        model.setQueryName("SQL_Injection");
        assertEquals("SQL_Injection", provider.getText(model));
    }

    @Test
    void testGetText_typeFunction_returnsType() {
        ColumnTextProvider provider = new ColumnTextProvider(DisplayModel::getType);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        model.setType("SAST");
        assertEquals("SAST", provider.getText(model));
    }

    @Test
    void testGetText_functionReturnsNull_propagatesNull() {
        ColumnTextProvider provider = new ColumnTextProvider(m -> null);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        assertNull(provider.getText(model));
    }

    @Test
    void testGetText_stateFunction_returnsState() {
        ColumnTextProvider provider = new ColumnTextProvider(DisplayModel::getState);
        DisplayModel model = new DisplayModel.DisplayModelBuilder("n").build();
        model.setState("TO_VERIFY");
        assertEquals("TO_VERIFY", provider.getText(model));
    }
}
