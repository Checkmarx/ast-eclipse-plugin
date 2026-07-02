package checkmarx.ast.eclipse.plugin.tests.unit.views;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.views.GlobalSettings;
import com.checkmarx.eclipse.views.filters.FilterState;

class GlobalSettingsExtendedTest {

	@Mock
	private Activator mockActivator;

	private MockedStatic<FilterState> filterStateMock;
	private MockedStatic<Activator> activatorMock;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		FilterState.resetFilters();
	}

	@Test
	void testConstructor_doesNotThrow() {
		assertDoesNotThrow(() -> new GlobalSettings());
	}

	@Test
	void testSetAndGetProjectId() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-123");
		assertEquals("proj-123", settings.getProjectId());
	}

	@Test
	void testSetAndGetProjectId_multipleChanges() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-1");
		assertEquals("proj-1", settings.getProjectId());

		settings.setProjectId("proj-2");
		assertEquals("proj-2", settings.getProjectId());
	}

	@Test
	void testSetAndGetProjectId_null() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId(null);
		assertNull(settings.getProjectId());
	}

	@Test
	void testSetAndGetBranch() {
		GlobalSettings settings = new GlobalSettings();
		settings.setBranch("main");
		assertEquals("main", settings.getBranch());
	}

	@Test
	void testSetAndGetBranch_multipleValues() {
		GlobalSettings settings = new GlobalSettings();
		String[] branches = {"main", "develop", "feature/xyz", "release/v1.0"};

		for (String branch : branches) {
			settings.setBranch(branch);
			assertEquals(branch, settings.getBranch());
		}
	}

	@Test
	void testSetAndGetBranch_empty() {
		GlobalSettings settings = new GlobalSettings();
		settings.setBranch("");
		assertEquals("", settings.getBranch());
	}

	@Test
	void testSetAndGetBranch_null() {
		GlobalSettings settings = new GlobalSettings();
		settings.setBranch(null);
		assertNull(settings.getBranch());
	}

	@Test
	void testSetAndGetScanId() {
		GlobalSettings settings = new GlobalSettings();
		settings.setScanId("scan-456");
		assertEquals("scan-456", settings.getScanId());
	}

	@Test
	void testSetAndGetScanId_UUID() {
		GlobalSettings settings = new GlobalSettings();
		String uuid = "550e8400-e29b-41d4-a716-446655440000";
		settings.setScanId(uuid);
		assertEquals(uuid, settings.getScanId());
	}

	@Test
	void testSetAndGetScanId_null() {
		GlobalSettings settings = new GlobalSettings();
		settings.setScanId(null);
		assertNull(settings.getScanId());
	}

	@Test
	void testSetMultipleFieldsSequentially() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-abc");
		settings.setBranch("develop");
		settings.setScanId("scan-xyz");

		assertEquals("proj-abc", settings.getProjectId());
		assertEquals("develop", settings.getBranch());
		assertEquals("scan-xyz", settings.getScanId());
	}

	@Test
	void testSetProjectIdBranchScanId_together() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-123");
		settings.setBranch("main");
		settings.setScanId("scan-789");

		assertEquals("proj-123", settings.getProjectId());
		assertEquals("main", settings.getBranch());
		assertEquals("scan-789", settings.getScanId());
	}

	@Test
	void testMultipleInstances_independentState() {
		GlobalSettings settings1 = new GlobalSettings();
		GlobalSettings settings2 = new GlobalSettings();

		settings1.setProjectId("proj-1");
		settings1.setBranch("branch-1");

		settings2.setProjectId("proj-2");
		settings2.setBranch("branch-2");

		assertEquals("proj-1", settings1.getProjectId());
		assertEquals("branch-1", settings1.getBranch());
		assertEquals("proj-2", settings2.getProjectId());
		assertEquals("branch-2", settings2.getBranch());
	}

	@Test
	void testEmptyStringValues() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("");
		settings.setBranch("");
		settings.setScanId("");

		assertEquals("", settings.getProjectId());
		assertEquals("", settings.getBranch());
		assertEquals("", settings.getScanId());
	}

	@Test
	void testSpecialCharactersInProjectId() {
		GlobalSettings settings = new GlobalSettings();
		String specialId = "proj-<>&\"'@#$%";
		settings.setProjectId(specialId);
		assertEquals(specialId, settings.getProjectId());
	}

	@Test
	void testSpecialCharactersInBranch() {
		GlobalSettings settings = new GlobalSettings();
		String specialBranch = "feature/<issue-123>-bugfix";
		settings.setBranch(specialBranch);
		assertEquals(specialBranch, settings.getBranch());
	}

	@Test
	void testSpecialCharactersInScanId() {
		GlobalSettings settings = new GlobalSettings();
		String specialScan = "scan-<test>-001";
		settings.setScanId(specialScan);
		assertEquals(specialScan, settings.getScanId());
	}

	@Test
	void testLongStringValues() {
		GlobalSettings settings = new GlobalSettings();
		String longString = "A".repeat(10000);
		settings.setProjectId(longString);
		assertEquals(longString, settings.getProjectId());
	}

	@Test
	void testWhitespaceInValues() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("  proj-123  ");
		settings.setBranch("\n\tmain\n\t");
		settings.setScanId("  scan-456  ");

		assertEquals("  proj-123  ", settings.getProjectId());
		assertEquals("\n\tmain\n\t", settings.getBranch());
		assertEquals("  scan-456  ", settings.getScanId());
	}

	@Test
	void testRepeatedGetterCalls_sameValue() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-test");

		String value1 = settings.getProjectId();
		String value2 = settings.getProjectId();
		String value3 = settings.getProjectId();

		assertEquals(value1, value2);
		assertEquals(value2, value3);
	}

	@Test
	void testSetNullThenSetValue() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId(null);
		assertNull(settings.getProjectId());

		settings.setProjectId("proj-new");
		assertEquals("proj-new", settings.getProjectId());
	}

	@Test
	void testSetValueThenSetNull() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj-old");
		assertEquals("proj-old", settings.getProjectId());

		settings.setProjectId(null);
		assertNull(settings.getProjectId());
	}

	@Test
	void testGetAllFields_afterConstruction() {
		GlobalSettings settings = new GlobalSettings();
		// All fields should be accessible
		settings.getProjectId();
		settings.getBranch();
		settings.getScanId();
		// No assertions needed, just checking no exceptions
	}

	@Test
	void testSetAllFieldsToNull() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("proj");
		settings.setBranch("branch");
		settings.setScanId("scan");

		settings.setProjectId(null);
		settings.setBranch(null);
		settings.setScanId(null);

		assertNull(settings.getProjectId());
		assertNull(settings.getBranch());
		assertNull(settings.getScanId());
	}

	@Test
	void testNumericStrings() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("12345");
		settings.setBranch("67890");
		settings.setScanId("11111");

		assertEquals("12345", settings.getProjectId());
		assertEquals("67890", settings.getBranch());
		assertEquals("11111", settings.getScanId());
	}

	@Test
	void testHexadecimalStrings() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("0xDEADBEEF");
		settings.setScanId("0xCAFEBABE");

		assertEquals("0xDEADBEEF", settings.getProjectId());
		assertEquals("0xCAFEBABE", settings.getScanId());
	}

	@Test
	void testUUIDFormats() {
		GlobalSettings settings = new GlobalSettings();
		String uuid1 = "550e8400-e29b-41d4-a716-446655440000";
		String uuid2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

		settings.setProjectId(uuid1);
		settings.setScanId(uuid2);

		assertEquals(uuid1, settings.getProjectId());
		assertEquals(uuid2, settings.getScanId());
	}

	@Test
	void testPathLikeStrings() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("/path/to/project");
		settings.setBranch("/refs/heads/main");

		assertEquals("/path/to/project", settings.getProjectId());
		assertEquals("/refs/heads/main", settings.getBranch());
	}

	@Test
	void testURLLikeStrings() {
		GlobalSettings settings = new GlobalSettings();
		settings.setProjectId("https://example.com/project");
		settings.setBranch("git@github.com:user/repo.git");

		assertEquals("https://example.com/project", settings.getProjectId());
		assertEquals("git@github.com:user/repo.git", settings.getBranch());
	}

	@Test
	void testFieldIndependence() {
		GlobalSettings settings = new GlobalSettings();

		settings.setProjectId("proj");
		assertEquals("proj", settings.getProjectId());
		assertNull(settings.getBranch());
		assertNull(settings.getScanId());

		settings.setBranch("branch");
		assertEquals("proj", settings.getProjectId());
		assertEquals("branch", settings.getBranch());
		assertNull(settings.getScanId());

		settings.setScanId("scan");
		assertEquals("proj", settings.getProjectId());
		assertEquals("branch", settings.getBranch());
		assertEquals("scan", settings.getScanId());
	}
}
