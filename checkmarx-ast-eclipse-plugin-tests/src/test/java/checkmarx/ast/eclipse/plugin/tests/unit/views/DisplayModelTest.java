package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.checkmarx.ast.results.result.Result;
import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.DisplayModel.DisplayModelBuilder;

class DisplayModelTest {

    // ─── builder ─────────────────────────────────────────────────────────────

    @Test
    void testBuilder_nameOnly_buildsSuccessfully() {
        DisplayModel model = new DisplayModelBuilder("test-name").build();
        assertNotNull(model);
        assertEquals("test-name", model.getName());
    }

    @Test
    void testBuilder_allFields_setsCorrectly() {
        Result mockResult = mock(Result.class);
        DisplayModel parent = new DisplayModelBuilder("parent").build();
        List<DisplayModel> children = new ArrayList<>();
        children.add(new DisplayModelBuilder("child").build());

        DisplayModel model = new DisplayModelBuilder("root")
                .setType("SAST")
                .setSeverity("HIGH")
                .setQueryName("SQL_Injection")
                .setSate("TO_VERIFY")
                .setParent(parent)
                .setChildren(children)
                .setResult(mockResult)
                .build();

        assertEquals("root", model.getName());
        assertEquals("SAST", model.getType());
        assertEquals("HIGH", model.getSeverity());
        assertEquals("SQL_Injection", model.getQueryName());
        assertEquals("TO_VERIFY", model.getState());
        assertSame(parent, model.getParent());
        assertEquals(1, model.getChildren().size());
        assertSame(mockResult, model.getResult());
    }

    @Test
    void testBuilder_childrenDefaultEmpty() {
        DisplayModel model = new DisplayModelBuilder("node").build();
        assertNotNull(model.children);
        assertTrue(model.children.isEmpty());
    }

    @Test
    void testBuilder_setName_overridesConstructorName() {
        DisplayModel model = new DisplayModelBuilder("original").setName("overridden").build();
        assertEquals("overridden", model.getName());
    }

    @Test
    void testBuilder_nullValues_noException() {
        assertDoesNotThrow(() -> {
            DisplayModel m = new DisplayModelBuilder("m")
                    .setType(null).setSeverity(null).setQueryName(null)
                    .setSate(null).setResult(null).build();
            assertNull(m.getType());
            assertNull(m.getSeverity());
        });
    }

    // ─── setters and getters ─────────────────────────────────────────────────

    @Test
    void testSetAndGetName() {
        DisplayModel m = new DisplayModelBuilder("initial").build();
        m.setName("updated");
        assertEquals("updated", m.getName());
    }

    @Test
    void testSetAndGetType() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.setType("SCA");
        assertEquals("SCA", m.getType());
    }

    @Test
    void testSetAndGetSeverity() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.setSeverity("CRITICAL");
        assertEquals("CRITICAL", m.getSeverity());
    }

    @Test
    void testSetAndGetQueryName() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.setQueryName("XSS");
        assertEquals("XSS", m.getQueryName());
    }

    @Test
    void testSetAndGetState() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.setState("CONFIRMED");
        assertEquals("CONFIRMED", m.getState());
    }

    @Test
    void testSetAndGetParent() {
        DisplayModel parent = new DisplayModelBuilder("parent").build();
        DisplayModel child = new DisplayModelBuilder("child").build();
        child.setParent(parent);
        assertSame(parent, child.getParent());
    }

    @Test
    void testSetAndGetChildren() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        List<DisplayModel> kids = new ArrayList<>();
        kids.add(new DisplayModelBuilder("k1").build());
        kids.add(new DisplayModelBuilder("k2").build());
        m.setChildren(kids);
        assertEquals(2, m.getChildren().size());
    }

    @Test
    void testSetAndGetResult() {
        Result mockResult = mock(Result.class);
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.setResult(mockResult);
        assertSame(mockResult, m.getResult());
    }

    // ─── public field direct access ───────────────────────────────────────────

    @Test
    void testPublicFieldDirectAccess() {
        DisplayModel m = new DisplayModelBuilder("m").build();
        m.name = "direct";
        m.type = "KICS";
        m.severity = "LOW";
        assertEquals("direct", m.name);
        assertEquals("KICS", m.type);
        assertEquals("LOW", m.severity);
    }
}
