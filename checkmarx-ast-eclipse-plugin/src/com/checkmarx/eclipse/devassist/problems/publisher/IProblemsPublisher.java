package com.checkmarx.eclipse.devassist.problems.publisher;

/**
 * Orchestrates turning the current set of findings into visible entries in the
 * native Eclipse Problems View.
 */
public interface IProblemsPublisher {

	/**
	 * Clears any previously published Checkmarx problems, fetches the current
	 * findings from the configured provider, creates markers for them and brings
	 * the Problems View to the foreground.
	 */
	void publish();

	/**
	 * Removes all Checkmarx problems from the Problems View.
	 */
	void clear();
}
