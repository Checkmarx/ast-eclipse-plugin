package checkmarx.ast.eclipse.plugin.tests.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import com.checkmarx.eclipse.views.DataProvider;
import com.checkmarx.eclipse.views.filters.FilterState;

public class CustomStateIntegrationTest extends BaseIntegrationTest {

	private final DataProvider provider = DataProvider.getInstance();

	// 1. SAST includes at least one custom state
	@Test
	public void testSastIncludesAtLeastOneCustomState() {
		List<String> sastStates = provider.getStatesForEngine("SAST");

		boolean hasCustomState = sastStates.stream()
				.anyMatch(s -> !FilterState.PREDEFINED_STATE_SET.contains(s.toUpperCase()));

		assertTrue("SAST should include at least one custom state", hasCustomState);
	}

	// 2. Check that KICS and SCA contain only predefined states
	@Test
	public void testNonSastEnginesIncludeOnlyPredefinedStates() {
		List<String> kicsStates = provider.getStatesForEngine("KICS");
		List<String> scaStates = provider.getStatesForEngine("SCA");
		//
		assertEquals("KICS should include only predefined states", FilterState.PREDEFINED_STATES.size(),
				kicsStates.size());
		assertEquals("SCA should include only predefined states", FilterState.PREDEFINED_STATES.size(),
				scaStates.size());

		for (String kics : kicsStates) {
			assertTrue("KICS state must be predefined", FilterState.PREDEFINED_STATE_SET.contains(kics.toUpperCase()));
		}

		for (String sca : scaStates) {
			assertTrue("SCA state must be predefined", FilterState.PREDEFINED_STATE_SET.contains(sca.toUpperCase()));
		}
	}

	// 3. No duplicates in SAST
	@Test
	public void testNoDuplicateStatesInSast() {
		List<String> sastStates = provider.getStatesForEngine("SAST");

		Set<String> deduplicated = new HashSet<>(sastStates);

		assertEquals("SAST states should not contain duplicates", sastStates.size(), deduplicated.size());
	}

	// 4. Optional: See if a custom state disappears on next fetch
	@Test
	public void testCustomStateStillExistsAfterRefetch() {
		List<String> initialStates = provider.getStatesForEngine("SAST");

		Optional<String> firstCustom = initialStates.stream()
				.filter(s -> !FilterState.PREDEFINED_STATE_SET.contains(s.toUpperCase())).findFirst();

		if (!firstCustom.isPresent()) {
			System.out.println("No custom state found in SAST — skipping recheck.");
			return;
		}

		String custom = firstCustom.get();
		List<String> refetched = provider.getStatesForEngine("SAST");

		assertTrue("Custom state should still exist after re-fetch", refetched.contains(custom));
	}
}
