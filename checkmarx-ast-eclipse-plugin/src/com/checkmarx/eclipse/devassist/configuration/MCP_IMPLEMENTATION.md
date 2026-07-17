# MCP Integration Implementation for Eclipse Plugin

## Overview
This document describes the implementation of MCP (Model Context Protocol) injection for the Checkmarx Eclipse plugin, mirroring the JetBrains implementation architecture.

## Components

### 1. McpSettingsInjector
**File:** `McpSettingsInjector.java`

Responsible for low-level MCP configuration file operations:
- **Location:** Platform-specific Copilot config directory
  - Windows: `%LOCALAPPDATA%/github-copilot/intellij/mcp.json`
  - macOS/Linux: `~/.config/github-copilot/intellij/mcp.json`

- **Operations:**
  - `installForCopilot(token)` - Adds/updates Checkmarx MCP server entry
  - `uninstallFromCopilot()` - Removes Checkmarx MCP server entry
  - `getMcpJsonPath()` - Returns the MCP config file path

- **JWT Token Processing:**
  - Extracts issuer claim from JWT tokens
  - Derives AST base URL from issuer domain
  - Constructs final MCP server URL with authentication headers

- **Logging:** Aggressive debug/info logging at every step

### 2. McpInstallService
**File:** `McpInstallService.java`

Orchestrates conditional MCP installation:
- **Conditions for Auto-Install:**
  1. User is authenticated (API key exists)
  2. MCP is enabled for tenant (checked via TenantSettingsProvider)
  3. Valid credential token is available

- **Operations:**
  - `attemptAutoInstall()` - Called during plugin startup
  - `installSilentlyAsync(credential)` - Background async installation
  - `uninstall()` - Called during plugin cleanup

- **Async Behavior:**
  - Returns `CompletableFuture<Boolean>` from async operations
  - Failures are logged but do not interrupt plugin startup
  - Installation happens on background thread to avoid blocking

- **Logging:** Info/debug logs for all authentication checks and installations

### 3. PluginLifecycleHandler
**File:** `PluginLifecycleHandler.java`

Handles plugin lifecycle events:
- **On Uninstall:**
  1. Clears persisted authentication session (API key)
  2. Removes Checkmarx MCP entry from Copilot config

- **Event Handling:**
  - Listens for `BundleEvent.UNINSTALLED` events
  - Also logs `UNRESOLVED` and `STOPPING` events

- **Logging:** Debug/info logs for lifecycle transitions

### 4. Integration with Existing Code

#### PluginStartup.java (Updated)
- Imports `McpInstallService`
- Calls `McpInstallService.attemptAutoInstall()` after loading mock problems
- MCP installation is non-blocking (async)

#### Preferences.java (Updated)
- Added `clearApiKey()` method for cleanup on uninstall

#### WelcomeDialog.java (Updated)
- Added debug logging of MCP status
- Shows MCP feature information when enabled
- No functional changes - UI/UX remains the same

## Data Flow

```
Plugin Startup
  ↓
PluginStartup.earlyStartup()
  ↓
Check: Is user authenticated? → NO → Skip MCP
  ↓ YES
McpInstallService.attemptAutoInstall()
  ↓
Check: Is MCP enabled for tenant? → NO → Skip MCP
  ↓ YES
McpInstallService.installSilentlyAsync(apiKey)
  ↓
[Background Thread]
McpSettingsInjector.installForCopilot(apiKey)
  ↓
  ├─ Extract issuer from JWT token
  ├─ Derive base URL from issuer
  ├─ Construct MCP server URL
  ├─ Merge entry into mcp.json
  └─ Log success/failure
```

## Logging Examples

### Successful Auto-Install
```
[STARTUP] Triggering MCP auto-install...
[MCP-INSTALL] Attempting auto-install of MCP configuration...
[MCP-INSTALL] User is authenticated, checking MCP server flag...
[MCP-INSTALL] ✓ All conditions met, installing MCP asynchronously...
[MCP-INSTALL] Background thread started, installing MCP...
[MCP-INJECTOR] Starting MCP installation for Copilot...
[MCP-INJECTOR] Token issuer extracted: https://iam.checkmarx.com/auth/realms/checkmarx
[MCP-INJECTOR] Derived base URL: https://ast.checkmarx.com
[MCP-INJECTOR] MCP URL: https://ast.checkmarx.com/api/security-mcp/mcp
[MCP-INJECTOR] Copilot MCP config path: /Users/user/.config/github-copilot/intellij/mcp.json
[MCP-INJECTOR] Reading existing MCP config...
[MCP-INJECTOR] Config changed: true
[MCP-INJECTOR] Updating MCP server entry in config
[MCP-INJECTOR] ✓ MCP config written to: /Users/user/.config/github-copilot/intellij/mcp.json
[MCP-INJECTOR] ✓ MCP configuration installed/updated successfully
[MCP-INSTALL] ✓ MCP installation completed successfully (config modified)
```

### Skip Due to Missing API Key
```
[MCP-INSTALL] Attempting auto-install of MCP configuration...
[MCP-INSTALL] Skipping MCP auto-install: user not authenticated (no API key)
```

### Skip Due to MCP Disabled
```
[MCP-INSTALL] Attempting auto-install of MCP configuration...
[MCP-INSTALL] User is authenticated, checking MCP server flag...
[MCP-INSTALL] Skipping MCP auto-install: AI MCP server disabled for tenant
```

## Configuration File Format

The `mcp.json` file structure (generated):
```json
{
  "servers": {
    "checkmarx-mcp": {
      "url": "https://ast.checkmarx.com/api/security-mcp/mcp",
      "requestInit": {
        "headers": {
          "cx-origin": "eclipse-plugin",
          "Authorization": "<API_KEY>"
        }
      }
    }
  }
}
```

## Testing the Integration

### Manual Testing Checklist

1. **Auto-Install on Authenticated Startup:**
   - Configure API key in preferences
   - Check if tenant has MCP enabled
   - Restart Eclipse
   - Check logs for MCP installation success
   - Verify `mcp.json` was created/updated at correct location

2. **Skip Auto-Install without API Key:**
   - Clear API key from preferences
   - Restart Eclipse
   - Verify logs show "user not authenticated"
   - Verify `mcp.json` was not created/modified

3. **Skip Auto-Install when MCP Disabled:**
   - Set API key
   - Configure tenant to have MCP disabled
   - Restart Eclipse
   - Verify logs show "MCP server disabled"

4. **Cleanup on Uninstall:**
   - Install with API key and MCP enabled
   - Verify `mcp.json` contains Checkmarx entry
   - Uninstall plugin
   - Verify `mcp.json` Checkmarx entry was removed
   - Verify API key was cleared

5. **Welcome Dialog:**
   - Verify MCP status is displayed correctly
   - Check logs show MCP status on dialog creation

## Debugging Tips

- All MCP operations log with `[MCP-INJECTOR]`, `[MCP-INSTALL]`, or `[LIFECYCLE]` prefixes
- Search logs for these prefixes to trace MCP operations
- Check `~/.log` (Eclipse) for complete session logs
- Use CxLogger.debug() calls to trace execution flow
- Verify file permissions on `mcp.json` location

## Differences from JetBrains

### Intentional Differences

1. **Thread Execution:**
   - JetBrains: Uses `AppExecutorUtil.getAppExecutorService()`
   - Eclipse: Uses `CompletableFuture.supplyAsync()`
   - Reason: Eclipse has different concurrency model

2. **Lifecycle Events:**
   - JetBrains: Implements `DynamicPluginListener`
   - Eclipse: Implements `BundleListener`
   - Reason: Eclipse plugin architecture uses OSGi bundles

3. **Preference Storage:**
   - JetBrains: Uses IDEA's `GlobalSettingsState`
   - Eclipse: Uses Eclipse's `ScopedPreferenceStore`
   - Reason: Different preference systems

4. **Origin Header:**
   - JetBrains: "jetbrains-agent"
   - Eclipse: "eclipse-plugin"
   - Reason: Different client identification

### Functional Similarities

- Same MCP server key: "checkmarx-mcp" (configurable)
- Same JWT token parsing logic
- Same URL derivation from issuer
- Same config file format
- Same async installation pattern
- Same aggressive logging pattern
