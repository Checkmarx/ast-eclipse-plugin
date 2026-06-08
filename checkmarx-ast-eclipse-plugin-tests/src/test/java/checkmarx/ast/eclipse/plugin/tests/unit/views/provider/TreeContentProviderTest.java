package checkmarx.ast.eclipse.plugin.tests.unit.views.provider;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.views.DisplayModel;
import com.checkmarx.eclipse.views.provider.TreeContentProvider;

class TreeContentProviderTest {

    private TreeContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TreeContentProvider();
    }

    // ─── getElements ─────────────────────────────────────────────────────────

    @Test
    void testGetElements_emptyChildren_returnsEmptyArray() {
        DisplayModel root = new DisplayModel.DisplayModelBuilder("root").build();
        Object[] elements = provider.getElements(root);
        assertNotNull(elements);
        assertEquals(0, elements.length);
    }

    @Test
    void testGetElements_withTwoChildren_returnsBothInOrder() {
        DisplayModel root = new DisplayModel.DisplayModelBuilder("root").build();
        DisplayModel c1 = new DisplayModel.DisplayModelBuilder("c1").build();
        DisplayModel c2 = new DisplayModel.DisplayModelBuilder("c2").build();
        root.children.add(c1);
        root.children.add(c2);

        Object[] elements = provider.getElements(root);

        assertEquals(2, elements.length);
        assertSame(c1, elements[0]);
        assertSame(c2, elements[1]);
    }

    // ─── getChildren ─────────────────────────────────────────────────────────

    @Test
    void testGetChildren_emptyChildren_returnsEmptyArray() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        assertEquals(0, provider.getChildren(node).length);
    }

    @Test
    void testGetChildren_withChild_returnsChildArray() {
        DisplayModel parent = new DisplayModel.DisplayModelBuilder("parent").build();
        DisplayModel child = new DisplayModel.DisplayModelBuilder("child").build();
        parent.children.add(child);

        Object[] children = provider.getChildren(parent);

        assertEquals(1, children.length);
        assertSame(child, children[0]);
    }

    // ─── getParent ────────────────────────────────────────────────────────────

    @Test
    void testGetParent_nullElement_returnsNull() {
        assertNull(provider.getParent(null));
    }

    @Test
    void testGetParent_withParentSet_returnsParent() {
        DisplayModel parent = new DisplayModel.DisplayModelBuilder("parent").build();
        DisplayModel child = new DisplayModel.DisplayModelBuilder("child").build();
        child.setParent(parent);

        assertSame(parent, provider.getParent(child));
    }

    @Test
    void testGetParent_noParentSet_returnsNull() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        assertNull(provider.getParent(node));
    }

    // ─── hasChildren ─────────────────────────────────────────────────────────

    @Test
    void testHasChildren_emptyList_returnsFalse() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        assertFalse(provider.hasChildren(node));
    }

    @Test
    void testHasChildren_withChild_returnsTrue() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        node.children.add(new DisplayModel.DisplayModelBuilder("child").build());
        assertTrue(provider.hasChildren(node));
    }

    @Test
    void testHasChildren_nullChildren_returnsFalse() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        node.children = null;
        assertFalse(provider.hasChildren(node));
    }

    @Test
    void testHasChildren_multipleChildren_returnsTrue() {
        DisplayModel node = new DisplayModel.DisplayModelBuilder("node").build();
        node.children.add(new DisplayModel.DisplayModelBuilder("c1").build());
        node.children.add(new DisplayModel.DisplayModelBuilder("c2").build());
        assertTrue(provider.hasChildren(node));
    }
}
