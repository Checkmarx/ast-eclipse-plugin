package checkmarx.ast.eclipse.plugin.tests.unit.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.Activator;
import com.checkmarx.eclipse.properties.Preferences;

class PreferencesTest {

	@Mock
	private IPreferencesService mockPreferencesService;

	@Mock
	private IPreferenceStore mockPreferenceStore;

	@Mock
	private Activator mockActivator;

	private MockedStatic<Platform> platformMock;
	private MockedStatic<Activator> activatorMock;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		platformMock = mockStatic(Platform.class);
		activatorMock = mockStatic(Activator.class);
	}

	@AfterEach
	void tearDown() {
		if (platformMock != null) {
			platformMock.close();
		}
		if (activatorMock != null) {
			activatorMock.close();
		}
	}

	@Test
	void testGetPref_returnsValue() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "testKey", null, null))
			.thenReturn("testValue");

		String result = Preferences.getPref("testKey");

		assertEquals("testValue", result);
		verify(mockPreferencesService).getString("com.checkmarx.eclipse", "testKey", null, null);
	}

	@Test
	void testGetPref_returnNull_whenNotFound() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "unknownKey", null, null))
			.thenReturn(null);

		String result = Preferences.getPref("unknownKey");

		assertNull(result);
	}

	@Test
	void testGetApiKey_callsGetPrefWithApiKeyConstant() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "apiKey", null, null))
			.thenReturn("testApiKeyValue");

		String result = Preferences.getApiKey();

		assertEquals("testApiKeyValue", result);
		verify(mockPreferencesService).getString("com.checkmarx.eclipse", "apiKey", null, null);
	}

	@Test
	void testGetApiKey_returnNull_whenNotSet() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "apiKey", null, null))
			.thenReturn(null);

		String result = Preferences.getApiKey();

		assertNull(result);
	}

	@Test
	void testGetAdditionalOptions_callsGetPrefWithAdditionalOptionsConstant() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "additionalOptions", null, null))
			.thenReturn("--scan-timeout 60");

		String result = Preferences.getAdditionalOptions();

		assertEquals("--scan-timeout 60", result);
		verify(mockPreferencesService).getString("com.checkmarx.eclipse", "additionalOptions", null, null);
	}

	@Test
	void testGetAdditionalOptions_returnNull_whenNotSet() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "additionalOptions", null, null))
			.thenReturn(null);

		String result = Preferences.getAdditionalOptions();

		assertNull(result);
	}

	@Test
	void testStore_setsValueInPreferenceStore() {
		activatorMock.when(Activator::getDefault).thenReturn(mockActivator);
		when(mockActivator.getPreferenceStore()).thenReturn(mockPreferenceStore);

		Preferences.store("testKey", "testValue");

		verify(mockActivator).getPreferenceStore();
		verify(mockPreferenceStore).setValue("testKey", "testValue");
	}

	@Test
	void testStore_withEmptyValue() {
		activatorMock.when(Activator::getDefault).thenReturn(mockActivator);
		when(mockActivator.getPreferenceStore()).thenReturn(mockPreferenceStore);

		Preferences.store("testKey", "");

		verify(mockPreferenceStore).setValue("testKey", "");
	}

	@Test
	void testStore_withNullValue() {
		activatorMock.when(Activator::getDefault).thenReturn(mockActivator);
		when(mockActivator.getPreferenceStore()).thenReturn(mockPreferenceStore);

		Preferences.store("testKey", null);

		verify(mockPreferenceStore).setValue("testKey", null);
	}

	@Test
	void testQualifierConstant() {
		assertEquals("com.checkmarx.eclipse", Preferences.QUALIFIER);
	}

	@Test
	void testApiKeyConstant() {
		assertEquals("apiKey", Preferences.API_KEY);
	}

	@Test
	void testAdditionalOptionsConstant() {
		assertEquals("additionalOptions", Preferences.ADDITIONAL_OPTIONS);
	}

	@Test
	void testGetPref_multipleCallsWithDifferentKeys() {
		platformMock.when(Platform::getPreferencesService).thenReturn(mockPreferencesService);
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "key1", null, null))
			.thenReturn("value1");
		when(mockPreferencesService.getString("com.checkmarx.eclipse", "key2", null, null))
			.thenReturn("value2");

		String result1 = Preferences.getPref("key1");
		String result2 = Preferences.getPref("key2");

		assertEquals("value1", result1);
		assertEquals("value2", result2);
	}

	@Test
	void testStore_multipleValuesSequentially() {
		activatorMock.when(Activator::getDefault).thenReturn(mockActivator);
		when(mockActivator.getPreferenceStore()).thenReturn(mockPreferenceStore);

		Preferences.store("key1", "value1");
		Preferences.store("key2", "value2");

		verify(mockPreferenceStore).setValue("key1", "value1");
		verify(mockPreferenceStore).setValue("key2", "value2");
	}
}
