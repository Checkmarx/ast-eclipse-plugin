package com.checkmarx.eclipse.devassist.ui.findings.model;

/**
 * Enumeration of scan engines supported by Checkmarx.
 */
public enum ScanEngine {
    ASCA("ASCA"),
    OSS("OSS"),
    SECRETS("SECRETS"),
    CONTAINERS("CONTAINERS"),
    IAC("IAC");

    private final String displayName;

    ScanEngine(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ScanEngine fromString(String value) {
        for (ScanEngine engine : ScanEngine.values()) {
            if (engine.displayName.equalsIgnoreCase(value)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("Unknown scan engine: " + value);
    }
}
