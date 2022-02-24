package com.checkmarx.eclipse.enums;

public enum State {

	TO_VERIFY,
	NOT_EXPLOITABLE,
	PROPOSED_NOT_EXPLOITABLE,
	CONFIRMED,
	NOT_IGNORED,
	IGNORED,
	URGENT;
	
	public static State getState(String state) {
		return State.valueOf(state);
	}
}
