package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.enums.PluginListenerType;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.PluginListenerDefinition;

class PluginListenerDefinitionTest {

    @Test
    void testConstructor_setsListenerType() {
        PluginListenerDefinition def = new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, null);
        assertEquals(PluginListenerType.FILTER_CHANGED, def.getListenerType());
    }

    @Test
    void testConstructor_setsResults() {
        List<DisplayModel> results = new ArrayList<>();
        results.add(new DisplayModel.DisplayModelBuilder("item").build());

        PluginListenerDefinition def = new PluginListenerDefinition(PluginListenerType.GET_RESULTS, results);

        assertSame(results, def.getResutls());
        assertEquals(1, def.getResutls().size());
    }

    @Test
    void testConstructor_nullResults_allowed() {
        PluginListenerDefinition def = new PluginListenerDefinition(PluginListenerType.CLEAN_AND_REFRESH, null);
        assertNull(def.getResutls());
    }

    @Test
    void testSetListenerType_updatesValue() {
        PluginListenerDefinition def = new PluginListenerDefinition(PluginListenerType.FILTER_CHANGED, null);
        def.setListenerType(PluginListenerType.LOAD_RESULTS_FOR_SCAN);
        assertEquals(PluginListenerType.LOAD_RESULTS_FOR_SCAN, def.getListenerType());
    }

    @Test
    void testSetResults_updatesValue() {
        PluginListenerDefinition def = new PluginListenerDefinition(PluginListenerType.GET_RESULTS, null);
        List<DisplayModel> newResults = new ArrayList<>();
        newResults.add(new DisplayModel.DisplayModelBuilder("new").build());
        def.setResutls(newResults);
        assertSame(newResults, def.getResutls());
    }

    @Test
    void testAllPluginListenerTypes_canBeSet() {
        for (PluginListenerType type : PluginListenerType.values()) {
            PluginListenerDefinition def = new PluginListenerDefinition(type, null);
            assertEquals(type, def.getListenerType());
        }
    }
}
