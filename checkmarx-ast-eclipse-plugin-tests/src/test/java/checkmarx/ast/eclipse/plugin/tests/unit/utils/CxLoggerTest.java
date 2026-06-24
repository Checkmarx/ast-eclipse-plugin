package checkmarx.ast.eclipse.plugin.tests.unit.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.checkmarx.eclipse.utils.CxLogger;

class CxLoggerTest {

    @Test
    void testWarning_doesNotThrow() {
        assertDoesNotThrow(() -> CxLogger.warning("test-warning-message"));
    }

    @Test
    void testError_withException_doesNotThrow() {
        assertDoesNotThrow(() -> CxLogger.error("test-error-message", new RuntimeException("test")));
    }

    @Test
    void testInfo_doesNotThrow() {
        assertDoesNotThrow(() -> CxLogger.info("test-info-message"));
    }
}
