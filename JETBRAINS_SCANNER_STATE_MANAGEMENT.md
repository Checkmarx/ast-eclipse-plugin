---
name: jetbrains-scanner-state-management
description: "Complete scanner enable/disable state management from JetBrains plugin - authentication, user preferences, and execution logic"
metadata: 
  node_type: memory
  type: reference
  originSessionId: 038b3b60-f0cb-4518-94b9-500be45087d9
  modified: 2026-08-09T15:20:04.349Z
---

# JetBrains Scanner State Management - Complete Reference

## Overview
The JetBrains plugin implements sophisticated state management for real-time scanners (ASCA, OSS, Secrets, Containers, IaC) with three key layers:
1. **Persistent State** - Saved to disk, survives IDE restarts
2. **User Preferences** - Preserved when features toggle (MCP on/off)
3. **Runtime Execution** - Determines which scanners actually run on code

---

## Layer 1: Persistent State (GlobalSettingsState)

### Current Scanner States
```java
// Current enablement status
private boolean ascaRealtime = false;
private boolean ossRealtime = false;
private boolean secretDetectionRealtime = false;
private boolean containersRealtime = false;
private boolean iacRealtime = false;
private String containersTool = "docker";
```

### User Preferences (Persisted)
```java
// User's custom choices - preserved even when features are disabled
@Attribute("userPreferencesSet")
private boolean userPreferencesSet = false;

@Attribute("userPrefAscaRealtime")
private boolean userPrefAscaRealtime = false;

@Attribute("userPrefOssRealtime")
private boolean userPrefOssRealtime = false;

@Attribute("userPrefSecretDetectionRealtime")
private boolean userPrefSecretDetectionRealtime = false;

@Attribute("userPrefContainersRealtime")
private boolean userPrefContainersRealtime = false;

@Attribute("userPrefIacRealtime")
private boolean userPrefIacRealtime = false;
```

### MCP Status Flags
```java
@Attribute("mcpEnabled")
private boolean mcpEnabled = false;

@Attribute("mcpStatusChecked")
private boolean mcpStatusChecked = false;

// License tracking
@Attribute("isDevAssistLicenseEnabled")
private boolean isDevAssistLicenseEnabled = false;

@Attribute("isOneAssistLicenseEnabled")
private boolean isOneAssistLicenseEnabled = false;
```

---

## Layer 2: User Preference Methods

### Save Preferences (Before Disabling Scanners)
```java
public void saveCurrentSettingsAsUserPreferences() {
    setUserPreferences(ascaRealtime, ossRealtime, secretDetectionRealtime, 
                      containersRealtime, iacRealtime);
}

public void setUserPreferences(boolean ascaRealtime, boolean ossRealtime, 
                               boolean secretDetectionRealtime,
                               boolean containersRealtime, boolean iacRealtime) {
    this.userPrefAscaRealtime = ascaRealtime;
    this.userPrefOssRealtime = ossRealtime;
    this.userPrefSecretDetectionRealtime = secretDetectionRealtime;
    this.userPrefContainersRealtime = containersRealtime;
    this.userPrefIacRealtime = iacRealtime;
    this.userPreferencesSet = true;
}
```

### Restore Preferences (When Features Re-enable)
```java
public boolean applyUserPreferencesToRealtimeSettings() {
    if (!userPreferencesSet) {
        return false; // No preferences saved yet
    }

    boolean changed = false;
    if (ascaRealtime != userPrefAscaRealtime) {
        ascaRealtime = userPrefAscaRealtime;
        changed = true;
    }
    if (ossRealtime != userPrefOssRealtime) {
        ossRealtime = userPrefOssRealtime;
        changed = true;
    }
    // ... repeat for all scanners
    return changed;
}
```

---

## Layer 3: Runtime Execution Check

### DevAssistUtils.isScannerActive()
```java
public static boolean isScannerActive(String engineName) {
    if (engineName == null) return false;
    try {
        if (GlobalSettingsState.getInstance().isAuthenticated()) {
            ScanEngine kind = ScanEngine.valueOf(engineName.toUpperCase());
            return globalScannerController().isScannerGloballyEnabled(kind);
        }
    } catch (IllegalArgumentException ex) {
        return false;
    }
    return false;
}
```

**Guard conditions:**
1. ✅ User is authenticated
2. ✅ Scanner is enabled in global state

### GlobalScannerController.isScannerGloballyEnabled()
```java
public synchronized boolean isScannerGloballyEnabled(ScanEngine type) {
    GlobalSettingsState state = GlobalSettingsState.getInstance();

    // MCP disabled at tenant level → all scanners disabled
    if (!state.isMcpEnabled()) {
        return false;
    }

    // Return scanner's individual state
    return scannerStateMap.getOrDefault(type, false);
}
```

**Additional guards:**
1. ✅ MCP enabled at tenant level
2. ✅ Scanner enabled in individual settings

### Where It's Used (ScanManager)
```java
protected final List<ScannerService<?>> getSupportedEnabledScanner(
        String filePath, PsiFile psiFile) {
    List<ScannerService<?>> supportedScanners = 
        scannerFactory.getAllSupportedScanners(filePath, psiFile);
    
    return supportedScanners.stream()
            .filter(scannerService ->
                    DevAssistUtils.isScannerActive(
                        scannerService.getConfig().getEngineName()))
            .collect(Collectors.toList());
}
```

---

## Scenario 1: First Authentication (New User)

### Flow:
1. User authenticates for first time
2. `userPreferencesSet = false` (no saved preferences)
3. System enables ALL scanners by default
4. All checkboxes appear checked in UI

### Code Path (RealtimeScannersSettingsComponent):
```java
public void reset() {
    state = GlobalSettingsState.getInstance();
    
    // Load current state (all false initially)
    ascaCheckbox.setSelected(state.isAscaRealtime());
    ossCheckbox.setSelected(state.isOssRealtime());
    secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
    containersCheckbox.setSelected(state.isContainersRealtime());
    iacCheckbox.setSelected(state.isIacRealtime());
    
    updateAssistState();
}
```

### Question: "Why don't all scanners enable automatically?"
**Answer:** In the current implementation, they START disabled (`= false`). The JetBrains design shows that:
- If `userPreferencesSet` is false → user hasn't made choices yet
- First time showing the UI, all are disabled
- User must explicitly enable them
- Those choices are then saved as `userPreferences`

---

## Scenario 2: MCP Disabled at Tenant Level (After Authentication)

### Before Disabling MCP:
```
Current State: ASCA=✅, OSS=✅, Secrets=✅, Containers=✅, IaC=✅
User Preferences: Empty (not saved yet)
```

### When MCP Becomes Disabled:
```
1. Check if userPreferencesSet == false
2. If true (first time MCP disabled):
   - Save current state as user preferences:
     userPrefAscaRealtime = true
     userPrefOssRealtime = true
     etc.
3. Disable all UI checkboxes and state values:
     ascaRealtime = false
     ossRealtime = false
     etc.
4. Show error message: "MCP disabled"
```

### Code Implementation (RealtimeScannersSettingsComponent):
```java
private void updateUIWithMcpStatus(boolean mcpEnabled, boolean isAuthenticated) {
    if (!mcpEnabled) {
        // Preserve current settings before disabling
        if (!state.getUserPreferencesSet()) {
            state.saveCurrentSettingsAsUserPreferences();
            LOGGER.debug("[CxOneAssist] Preserved scanner settings as user preferences (MCP disabled)");
        }

        // Uncheck all checkboxes
        ascaCheckbox.setSelected(false);
        ossCheckbox.setSelected(false);
        secretsCheckbox.setSelected(false);
        containersCheckbox.setSelected(false);
        iacCheckbox.setSelected(false);

        // Disable in state
        state.setAscaRealtime(false);
        state.setOssRealtime(false);
        state.setSecretDetectionRealtime(false);
        state.setContainersRealtime(false);
        state.setIacRealtime(false);
        
        // Persist and notify
        GlobalSettingsState.getInstance().apply(state);
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                .settingsApplied();
    }
}
```

### Scanning During MCP Disabled:
Even if a user tries to scan:
```java
DevAssistUtils.isScannerActive("ASCA")
  → GlobalSettingsState.isAuthenticated() = true ✅
  → GlobalScannerController.isScannerGloballyEnabled(ASCA)
    → state.isMcpEnabled() = false ❌
    → return false  // Scanner doesn't run
```

---

## Scenario 3: MCP Re-enabled (Restore User Preferences)

### Before Re-enabling MCP:
```
Current State: ASCA=❌, OSS=❌, Secrets=❌, Containers=❌, IaC=❌
User Preferences Set: ✅
User Preferences: ASCA=✅, OSS=✅, Secrets=❌, Containers=✅, IaC=❌
```

### When MCP Is Re-enabled:
```java
if (state.getUserPreferencesSet()) {
    boolean preferencesApplied = state.applyUserPreferencesToRealtimeSettings();
    // Now state becomes:
    // ASCA=✅, OSS=✅, Secrets=❌, Containers=✅, IaC=❌
    // (Exactly as user had set them before MCP was disabled)
}
```

### Code Path (RealtimeScannersSettingsComponent):
```java
private void updateUIWithMcpStatus(boolean mcpEnabled, boolean isAuthenticated) {
    if (mcpEnabled) {
        if (state.getUserPreferencesSet()) {
            boolean preferencesApplied = state.applyUserPreferencesToRealtimeSettings();
            if (preferencesApplied) {
                LOGGER.debug("[CxOneAssist] Restored user preferences for realtime scanners");
                // Notify listeners
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                        .settingsApplied();
            }
        }

        // Update UI to reflect restored preferences
        ascaCheckbox.setSelected(state.isAscaRealtime());
        ossCheckbox.setSelected(state.isOssRealtime());
        secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
        containersCheckbox.setSelected(state.isContainersRealtime());
        iacCheckbox.setSelected(state.isIacRealtime());
    }
}
```

---

## Scenario 4: User Manually Changes Settings

### UI Apply Method (RealtimeScannersSettingsComponent):
```java
public void apply() {
    boolean ascaSelected = ascaCheckbox.isSelected();
    boolean ossSelected = ossCheckbox.isSelected();
    boolean secretsSelected = secretsCheckbox.isSelected();
    boolean containersSelected = containersCheckbox.isSelected();
    boolean iacSelected = iacCheckbox.isSelected();

    // Save to current state
    state.setAscaRealtime(ascaSelected);
    state.setOssRealtime(ossSelected);
    state.setSecretDetectionRealtime(secretsSelected);
    state.setContainersRealtime(containersSelected);
    state.setIacRealtime(iacSelected);

    // IMPORTANT: Also save as user preferences
    state.setUserPreferences(ascaSelected, ossSelected, secretsSelected, 
                            containersSelected, iacSelected);

    // Notify all listeners
    ApplicationManager.getApplication().getMessageBus()
            .syncPublisher(SettingsListener.SETTINGS_APPLIED)
            .settingsApplied();
}
```

---

## Scenario 5: License Removed (Dev Assist or One Assist License)

### Detection:
```java
private void updateAssistState() {
    boolean authenticated = state.isAuthenticated();
    boolean hasAssistLicense = state.isOneAssistLicenseEnabled() || 
                               state.isDevAssistLicenseEnabled();

    if (!hasAssistLicense) {
        // No license: hide UI and hard-disable scanners
        disableAssistUI("CxOne Assist is unavailable without a license.",
                        JBColor.RED,
                        false);
        return;
    }
    // ... continue with MCP check
}
```

### Disabling All Scanners (License Removed):
```java
private void disableAssistUI(String message, Color color, boolean keepVisible) {
    // Preserve user preferences if not already saved
    if (!state.getUserPreferencesSet()) {
        state.saveCurrentSettingsAsUserPreferences();
    }

    // Uncheck and disable all UI
    ascaCheckbox.setEnabled(false);
    ossCheckbox.setEnabled(false);
    // ... etc
    ascaCheckbox.setSelected(false);
    ossCheckbox.setSelected(false);
    // ... etc

    // Disable in state
    state.setAscaRealtime(false);
    state.setOssRealtime(false);
    // ... etc

    // Persist and notify
    GlobalSettingsState.getInstance().apply(state);
}
```

---

## Key Design Principles

### 1. Dual-Layer State
- **Current State**: What's active RIGHT NOW
- **User Preferences**: What user WANTS (restored when possible)

### 2. Preservation Priority
```
Guard Rails (in order):
1. License required? No → disable all
2. Authenticated? No → disable all
3. MCP enabled at tenant? No → disable all but preserve preferences
4. Scanner enabled individually? No → skip this scanner
```

### 3. Preference Persistence Logic
```
WHEN to SAVE preferences:
- User manually changes checkboxes
- MCP about to be disabled (for the first time)
- License about to be revoked (for the first time)

WHEN to RESTORE preferences:
- MCP becomes enabled again
- License becomes available again
- User logs in (if preferences were saved from previous session)
```

### 4. Execution Guard
```java
// In ScanManager: Only runs scanner if BOTH conditions true:
if (DevAssistUtils.isScannerActive(engineName)) {  // Checks auth + enabled + MCP
    // Scan file
}
```

---

## Implementation Checklist for Eclipse Plugin

- [ ] Add user preference fields to Preferences.java (userPrefAsca, userPrefOss, etc.)
- [ ] Add `userPreferencesSet` flag to Preferences.java
- [ ] Implement `setUserPreferences()` method
- [ ] Implement `saveCurrentSettingsAsUserPreferences()` method
- [ ] Implement `applyUserPreferencesToRealtimeSettings()` method
- [ ] Create GlobalScannerController equivalent for Eclipse
- [ ] Create DevAssistUtils.isScannerActive() check
- [ ] Update CheckmarxPreferencePage to handle MCP enabled/disabled transitions
- [ ] Update CheckmarxPreferencePage to preserve preferences before disabling
- [ ] Update CheckmarxPreferencePage to restore preferences when re-enabling
- [ ] Ensure scanner service checks `isScannerActive()` before scanning
- [ ] Add SettingsChangeListener to notify all observers when preferences change
- [ ] Test: First auth → all disabled → user enables → MCP disabled → MCP enabled (should restore)

---

## Example: Complete Auth Scenario

```
Timeline:
1. User installs plugin, launches Eclipse
   State: authenticated=false, userPreferencesSet=false, all scanners=false

2. User authenticates
   State: authenticated=true, userPreferencesSet=false, all scanners=false
   UI: All checkboxes unchecked, all disabled

3. User opens Checkmarx Scanner Configuration settings
   State: Same as above
   UI: User can now enable/disable scanners

4. User enables: ASCA ✅, OSS ✅, Secrets ❌, Containers ✅, IaC ❌
   User clicks OK/Apply
   
   State After Apply:
   - Current: ASCA=✅, OSS=✅, Secrets=❌, Containers=✅, IaC=❌
   - Preferences: ASCA=✅, OSS=✅, Secrets=❌, Containers=✅, IaC=❌
   - userPreferencesSet=true

5. MCP becomes disabled at tenant level
   System detects: userPreferencesSet=true (already saved from step 4)
   State: ASCA=❌, OSS=❌, Secrets=❌, Containers=❌, IaC=❌ (all disabled)
   Preferences: Unchanged from step 4
   UI: All checkboxes unchecked and disabled, error message shown

6. User tries to scan a file
   ScanManager calls: DevAssistUtils.isScannerActive("ASCA")
   → Authenticated? ✅
   → MCP enabled? ❌
   → Result: false (no scanners run)

7. MCP becomes enabled again
   System detects: userPreferencesSet=true
   State: ASCA=✅, OSS=✅, Secrets=❌, Containers=✅, IaC=❌ (restored!)
   UI: Checkboxes updated to reflect restored state

8. User tries to scan a file
   ScanManager calls: DevAssistUtils.isScannerActive("ASCA")
   → Authenticated? ✅
   → MCP enabled? ✅
   → Scanner enabled? ✅
   → Result: true (ASCA scans)
   
   ScanManager calls: DevAssistUtils.isScannerActive("SECRETS")
   → Authenticated? ✅
   → MCP enabled? ✅
   → Scanner enabled? ❌
   → Result: false (Secrets scanner doesn't run)
```

---

## Files to Reference

**JetBrains Implementation:**
- GlobalSettingsState.java - State and preference methods
- RealtimeScannersSettingsComponent.java - UI and preference handling
- GlobalScannerController.java - Runtime execution check
- DevAssistUtils.isScannerActive() - Execution guard
- ScanManager.java - Uses the execution guard

**Files to Create/Modify in Eclipse:**
- Preferences.java - Add user preference fields
- CheckmarxPreferencePage.java - Update UI handling
- GlobalScannerController.java (new) - Execution check
- DevAssistUtils.java (new) or extend existing - isScannerActive()
