package com.checkmarx.eclipse.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class State {

	private static final Map<String, State> STATES = new LinkedHashMap<>();

	// Predefined states
	public static final State TO_VERIFY = new State("TO_VERIFY");
	public static final State NOT_EXPLOITABLE = new State("NOT_EXPLOITABLE");
	public static final State PROPOSED_NOT_EXPLOITABLE = new State("PROPOSED_NOT_EXPLOITABLE");
	public static final State CONFIRMED = new State("CONFIRMED");
	public static final State NOT_IGNORED = new State("NOT_IGNORED");
	public static final State IGNORED = new State("IGNORED");
	public static final State URGENT = new State("URGENT");

	private final String name;

	private State(String name) {
		this.name = name;
		STATES.put(name, this);
	}

	public String getName() {
		return name;
	}

	public static State of(String name) {
		return STATES.computeIfAbsent(name, State::new); // register custom states dynamically
	}

	public static State getState(String name) {
		return STATES.get(name);
	}

	public static Map<String, State> values() {
		return Collections.unmodifiableMap(STATES);
	}

	@Override
	public String toString() {
		return name;
	}
}
