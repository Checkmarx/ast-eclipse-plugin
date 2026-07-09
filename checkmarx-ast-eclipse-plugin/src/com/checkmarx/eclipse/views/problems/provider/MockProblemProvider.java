package com.checkmarx.eclipse.views.problems.provider;

import java.util.ArrayList;
import java.util.List;

import com.checkmarx.eclipse.enums.Severity;
import com.checkmarx.eclipse.views.problems.model.ScanProblem;

/**
 * Phase 1 provider that returns a fixed, hand-written set of ten findings
 * spread across a handful of (fictional) source files.
 *
 * <p>
 * The data deliberately looks like real Checkmarx SAST results (query names,
 * rule ids, severities, line numbers, {@code TO_VERIFY} status) so that
 * swapping this class for a scan-backed {@link IProblemProvider} is a one-line
 * change in the composition root and requires no change anywhere else.
 * </p>
 *
 * <p>
 * Because the file names are fictional, markers will only render with working
 * navigation/gutter icons if a workspace file with a matching name exists;
 * otherwise the publisher attaches the marker to the workspace root so the
 * finding is still visible in the Problems View. See the publisher for details.
 * </p>
 */
public class MockProblemProvider implements IProblemProvider {

	@Override
	public List<ScanProblem> getProblems() {
		System.out.println("[PROBLEMS] Loading mock problems from MockProblemProvider...");
		List<ScanProblem> problems = new ArrayList<>(10);

		// Real workspace files with actual line numbers
		String workspaceRoot = "C:\\Utils\\EclipseWorkspace\\runtime-New_configuration\\test_java_project\\src\\test_java_project\\";

		// --- LoginController.java (Hardcoded Credentials + XSS) ------------------
		problems.add(new ScanProblem.Builder("f0000001")
				.ruleId("Hardcoded_Credentials")
				.message("Use of Hard-coded Credentials: API key exposed in source code")
				.fileName(workspaceRoot + "LoginController.java").line(4).column(35)
				.severity(Severity.CRITICAL).build());

		problems.add(new ScanProblem.Builder("f0000002")
				.ruleId("Hardcoded_Credentials")
				.message("Use of Hard-coded Credentials: Database password exposed")
				.fileName(workspaceRoot + "LoginController.java").line(5).column(27)
				.severity(Severity.CRITICAL).build());

		problems.add(new ScanProblem.Builder("f0000003")
				.ruleId("Reflected_XSS_All_Clients")
				.message("Reflected XSS: user input echoed without encoding into HTML")
				.fileName(workspaceRoot + "LoginController.java").line(10).column(17)
				.severity(Severity.HIGH).build());

		// --- UserService.java (Deserialization) ---------------------------------
		problems.add(new ScanProblem.Builder("f0000004")
				.ruleId("Insecure_Deserialization")
				.message("Insecure Deserialization: untrusted object stream deserialized")
				.fileName(workspaceRoot + "UserService.java").line(10).column(18)
				.severity(Severity.CRITICAL).build());

		problems.add(new ScanProblem.Builder("f0000005")
				.ruleId("Missing_Input_Validation")
				.message("Missing Input Validation: file stream not validated before deserialization")
				.fileName(workspaceRoot + "UserService.java").line(8).column(35)
				.severity(Severity.HIGH).build());

		// --- PaymentGateway.java (SQL Injection) --------------------------------
		problems.add(new ScanProblem.Builder("f0000006")
				.ruleId("SQL_Injection")
				.message("SQL Injection: unsanitized input flows into SQL query")
				.fileName(workspaceRoot + "PaymentGateway.java").line(8).column(30)
				.severity(Severity.CRITICAL).build());
		problems.add(new ScanProblem.Builder("f0000006")
				.ruleId("SQL_Injection")
				.message("SQL Injection: unsanitized input flows into SQL query")
				.fileName(workspaceRoot + "package.json").line(8).column(30)
				.severity(Severity.CRITICAL).build());

		problems.add(new ScanProblem.Builder("f0000007")
				.ruleId("SQL_Injection")
				.message("SQL Injection: dynamic SQL query vulnerable to attack")
				.fileName(workspaceRoot + "PaymentGateway.java").line(9).column(19)
				.severity(Severity.HIGH).build());

		// --- FileUploadHandler.java (Path Traversal) ----------------------------
		problems.add(new ScanProblem.Builder("f0000008")
				.ruleId("Path_Traversal")
				.message("Path Traversal: file path constructed from unvalidated input")
				.fileName(workspaceRoot + "FileUploadHandler.java").line(8).column(25)
				.severity(Severity.CRITICAL).build());

		problems.add(new ScanProblem.Builder("f0000009")
				.ruleId("Path_Traversal")
				.message("Path Traversal: fileName parameter not sanitized for directory traversal")
				.fileName(workspaceRoot + "FileUploadHandler.java").line(9).column(15)
				.severity(Severity.HIGH).build());

		problems.add(new ScanProblem.Builder("f0000010")
				.ruleId("Unhandled_Exception")
				.message("IOException should be properly handled or declared in throws clause")
				.fileName(workspaceRoot + "FileUploadHandler.java").line(11).column(21)
				.severity(Severity.MEDIUM).build());
		problems.add(new ScanProblem.Builder("f0000010")
				.ruleId("Unhandled_Exception")
				.message("IOException should be properly handled or declared in throws clause")
				.fileName(workspaceRoot + "pom.xml").line(11).column(21)
				.severity(Severity.MEDIUM).build());
		problems.add(new ScanProblem.Builder("f0000010")
				.ruleId("Unhandled_Exception")
				.message("IOException should be properly handled or declared in throws clause")
				.fileName(workspaceRoot + "pom.xml").line(11).column(21)
				.severity(Severity.MEDIUM).build());

		System.out.println("[PROBLEMS] ✓ Loaded " + problems.size() + " mock problems:");
		System.out.println("[PROBLEMS]   - 4 CRITICAL issues");
		System.out.println("[PROBLEMS]   - 4 HIGH issues");
		System.out.println("[PROBLEMS]   - 1 MEDIUM issues");
		System.out.println("[PROBLEMS] ✓ Using REAL workspace files from test_java_project");

		return problems;
	}
}
