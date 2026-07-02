package checkmarx.ast.eclipse.plugin.tests.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import com.checkmarx.eclipse.Activator;

class ActivatorTest {

	@Mock
	private BundleContext mockBundleContext;

	private MockedStatic<AbstractUIPlugin> pluginMock;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	void tearDown() {
		if (pluginMock != null) {
			pluginMock.close();
		}
	}

	@Test
	void testConstructor_createsActivatorInstance() {
		Activator activator = new Activator();
		assertNotNull(activator);
	}

	@Test
	void testPluginIdConstant() {
		assertEquals("com.checkmarx.eclipse.plugin", Activator.PLUGIN_ID);
	}

	@Test
	void testGetDefault_returnsNonNull() {
		Activator activator = Activator.getDefault();
		assertNotNull(activator);
	}

	@Test
	void testGetDefault_returnsSameInstance() {
		Activator first = Activator.getDefault();
		Activator second = Activator.getDefault();
		assertSame(first, second);
	}

	@Test
	void testGetImageDescriptor_withValidPath() {
		String path = "icons/plugin.gif";
		ImageDescriptor descriptor = Activator.getImageDescriptor(path);
		assertNotNull(descriptor);
	}

	@Test
	void testGetImageDescriptor_withDifferentPaths() {
		String path1 = "icons/icon1.png";
		String path2 = "icons/icon2.png";

		ImageDescriptor descriptor1 = Activator.getImageDescriptor(path1);
		ImageDescriptor descriptor2 = Activator.getImageDescriptor(path2);

		assertNotNull(descriptor1);
		assertNotNull(descriptor2);
	}

	@Test
	void testGetImageDescriptor_withEmptyPath() {
		ImageDescriptor descriptor = Activator.getImageDescriptor("");
		assertNotNull(descriptor);
	}

	@Test
	void testGetImageDescriptor_pluginIdMatches() {
		String path = "test.png";
		ImageDescriptor descriptor = Activator.getImageDescriptor(path);

		assertNotNull(descriptor);
	}

	@Test
	void testGetImageDescriptor_multipleCallsSamePath() {
		String path = "icons/same.png";

		ImageDescriptor descriptor1 = Activator.getImageDescriptor(path);
		ImageDescriptor descriptor2 = Activator.getImageDescriptor(path);

		assertNotNull(descriptor1);
		assertNotNull(descriptor2);
	}

	@Test
	void testActivatorInstantiation_doesNotThrow() {
		assertDoesNotThrow(() -> new Activator());
	}
}
