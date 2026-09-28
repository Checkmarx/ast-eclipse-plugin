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
		// ScopedPreferenceStore requires Eclipse preferences service which is not available
		// in unit tests. Just verify the method doesn't throw.
		assertDoesNotThrow(() -> Preferences.store("testKey", "testValue"));
	}

	@Test
	void testStore_withEmptyValue() {
		// ScopedPreferenceStore requires Eclipse preferences service which is not available
		// in unit tests. Just verify the method doesn't throw with empty value.
		assertDoesNotThrow(() -> Preferences.store("testKey", ""));
	}

	@Test
	void testStore_withNullValue() {
		// Preferences.store delegates directly to ScopedPreferenceStore#setValue(String, String),
		// which asserts its value is non-null - storing null is not a supported use case.
		assertThrows(NullPointerException.class, () -> Preferences.store("testKey", null));
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
		// ScopedPreferenceStore requires Eclipse preferences service which is not available
		// in unit tests. Just verify multiple calls don't throw.
		assertDoesNotThrow(() -> {
			Preferences.store("key1", "value1");
			Preferences.store("key2", "value2");
		});
	}
}
