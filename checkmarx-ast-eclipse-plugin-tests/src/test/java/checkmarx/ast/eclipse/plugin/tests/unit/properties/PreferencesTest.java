package checkmarx.ast.eclipse.plugin.tests.unit.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import com.checkmarx.eclipse.common.preferences.Preferences;

class PreferencesTest {

	@Mock
	private IPreferencesService mockPreferencesService;

	private static final String KEY_PREFIX = "preferencesTest.";

	private MockedStatic<Platform> platformMock;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		platformMock = mockStatic(Platform.class);
	}

	@AfterEach
	void tearDown() {
		if (platformMock != null) {
			platformMock.close();
		}
		for (String key : new String[] { "testKey", "key1", "key2" }) {
			Preferences.STORE.setToDefault(KEY_PREFIX + key);
		}
	}

	/**
	 * Preferences.store writes to the real ScopedPreferenceStore, which reads
	 * through Platform.getPreferencesService(), so the Platform mock must be released.
	 */
	private void useRealPlatform() {
		platformMock.close();
		platformMock = null;
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
		useRealPlatform();

		Preferences.store(KEY_PREFIX + "testKey", "testValue");

		assertEquals("testValue", Preferences.STORE.getString(KEY_PREFIX + "testKey"));
	}

	@Test
	void testStore_withEmptyValue() {
		useRealPlatform();
		Preferences.store(KEY_PREFIX + "testKey", "testValue");

		Preferences.store(KEY_PREFIX + "testKey", "");

		assertEquals("", Preferences.STORE.getString(KEY_PREFIX + "testKey"));
	}

	@Test
	void testStore_withNullValue_throws() {
		useRealPlatform();

		// Eclipse preference nodes reject null values
		assertThrows(NullPointerException.class, () -> Preferences.store(KEY_PREFIX + "testKey", null));
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
		useRealPlatform();

		Preferences.store(KEY_PREFIX + "key1", "value1");
		Preferences.store(KEY_PREFIX + "key2", "value2");

		assertEquals("value1", Preferences.STORE.getString(KEY_PREFIX + "key1"));
		assertEquals("value2", Preferences.STORE.getString(KEY_PREFIX + "key2"));
	}
}
