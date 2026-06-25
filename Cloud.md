# Cloud.md — Checkmarx One Eclipse Plugin

> Standardized Cloud MD file for [ast-eclipse-plugin](https://github.com/Checkmarx/ast-eclipse-plugin)
> Following the Cloud MD standardization template defined in epic AST-146793.

---

## Project Overview

The **Checkmarx One Eclipse Plugin** integrates the full Checkmarx One security platform directly into the Eclipse IDE. It enables developers to discover and remediate vulnerabilities without leaving their editor — embodying the shift-left AppSec philosophy.

**Key capabilities:**
- Import scan results (SAST, SCA, IaC Security) from Checkmarx One directly into Eclipse
- Run new scans from the IDE before committing code
- Navigate from a vulnerability directly to the affected source line
- Triage results (adjust severity, state, add comments) without leaving the IDE
- Filter and group results by severity, state, or query name
- View vulnerability descriptions, attack vectors, and Codebashing remediation links
- Best Fix Location (BFL) highlighting for SAST findings

**Supported Eclipse versions:** 2019-03 (4.11) and above
**Supported platforms:** Windows, macOS, Linux/GTK

---

## Architecture

The plugin follows a standard Eclipse **ViewPart** architecture backed by an **OSGi** bundle lifecycle.

```
┌─────────────────────────────────────────────────────┐
│                  Eclipse IDE                        │
│  ┌──────────────────────────────────────────────┐   │
│  │              CheckmarxView (ViewPart)         │   │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────┐  │   │
│  │  │  Project │  │  Branch  │  │   Scan ID  │  │   │
│  │  │  Combo   │  │  Combo   │  │   Combo    │  │   │
│  │  └──────────┘  └──────────┘  └────────────┘  │   │
│  │  ┌────────────────────────────────────────┐   │   │
│  │  │         Results Tree (SWT TreeViewer)  │   │   │
│  │  │  Grouped by: Severity / Query / State  │   │   │
│  │  └────────────────────────────────────────┘   │   │
│  │  ┌───────────────┐  ┌──────────────────────┐  │   │
│  │  │  Description  │  │   Attack Vector /    │  │   │
│  │  │  & Triage     │  │   Package Data /     │  │   │
│  │  │  Panel        │  │   BFL Panel          │  │   │
│  │  └───────────────┘  └──────────────────────┘  │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
         │  EventBus (Guava)
         ▼
┌─────────────────────┐        ┌──────────────────────┐
│    DataProvider     │◄──────►│  ast-cli-java-wrapper │
│  (Singleton)        │        │  (Checkmarx One API)  │
└─────────────────────┘        └──────────────────────┘
```

**Key architectural decisions:**
- **Event-driven UI:** Google Guava `EventBus` decouples UI actions (filter changes, scan loads) from the view rendering. Events: `FILTER_CHANGED`, `GET_RESULTS`, `CLEAN_AND_REFRESH`, `LOAD_RESULTS_FOR_SCAN`.
- **CLI wrapper:** All communication with the Checkmarx One platform is delegated to `ast-cli-java-wrapper`, which wraps the Checkmarx CLI binary. No direct REST calls from the plugin.
- **Singleton DataProvider:** Holds all loaded scan results, filter state, and project/branch/scan metadata for the current session.
- **Static FilterState:** Severity and state filter flags are stored as static fields persisted to Eclipse preferences via `GlobalSettings`.

---

## Repository Structure

```
ast-eclipse-plugin/
├── checkmarx-ast-eclipse-plugin/          # Main OSGi plugin bundle
│   ├── src/com/checkmarx/eclipse/
│   │   ├── Activator.java                 # OSGi bundle lifecycle
│   │   ├── enums/                         # Severity, State, ActionName enums
│   │   ├── properties/                    # Eclipse preferences page & fields
│   │   ├── runner/                        # Authentication runner
│   │   ├── utils/                         # CxLogger, PluginUtils, PluginConstants
│   │   └── views/
│   │       ├── CheckmarxView.java         # Main ViewPart (~2600 lines)
│   │       ├── DataProvider.java          # Singleton data/state manager
│   │       ├── DisplayModel.java          # Tree node model
│   │       ├── GlobalSettings.java        # Eclipse preference store wrapper
│   │       ├── actions/                   # Toolbar actions (filters, scan, triage)
│   │       ├── filters/                   # FilterState, ActionFilters
│   │       └── provider/                  # TreeContentProvider, ColumnProvider
│   ├── META-INF/MANIFEST.MF               # OSGi bundle descriptor
│   ├── plugin.xml                         # Eclipse extension points
│   ├── icons/                             # Severity and UI icons
│   └── lib/                               # Bundled JAR dependencies
├── checkmarx-ast-eclipse-plugin-tests/    # Test bundle
│   └── src/test/java/.../tests/
│       ├── integration/                   # Integration tests (auth)
│       ├── ui/                            # SWTBot UI tests
│       └── unit/                          # Unit tests
├── com.checkmarx.eclipse.feature/         # Eclipse feature descriptor
├── com.checkmarx.eclipse.site/            # Eclipse p2 update site
├── pom.xml                                # Root Maven/Tycho POM
├── ast-cli-java-wrapper.version           # Pinned wrapper version
└── .github/workflows/                     # CI/CD pipelines
```

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17 (Temurin) |
| IDE Framework | Eclipse OSGi / RCP | 4.11+ |
| UI Toolkit | SWT / JFace | Bundled with Eclipse |
| Build System | Maven + Eclipse Tycho | Tycho 4.0.11 |
| Platform API | ast-cli-java-wrapper | 2.4.23 |
| Event Bus | Google Guava | Bundled with Eclipse |
| Git Integration | JGit | Bundled with Eclipse |
| JSON | Jackson | 2.21.1 |
| Utilities | Apache Commons Lang3 | 3.18.0 |
| Logging | SLF4J + Eclipse ILog (CxLogger) | 2.0.17 |

---

## Development Setup

### Prerequisites

1. **Java 17** (Temurin recommended)
2. **Eclipse IDE for RCP and RAP Developers** (2019-03 or later) — includes PDE (Plugin Development Environment)
3. **Maven 3.x** with Tycho support
4. **Checkmarx One account** with an API key (`ast-scanner` + `default-roles` IAM roles)

### Clone and Import

```bash
git clone https://github.com/Checkmarx/ast-eclipse-plugin.git
cd ast-eclipse-plugin
```

Import into Eclipse:
- `File → Import → Maven → Existing Maven Projects`
- Select the repo root — all four modules will be detected

### Build from CLI

```bash
# Full build (plugin + feature + site + tests)
mvn clean verify

# Build plugin only (skip tests)
mvn clean package -pl checkmarx-ast-eclipse-plugin -am -DskipTests
```

### Run in Development

1. Open `checkmarx-ast-eclipse-plugin/plugin.xml` in Eclipse
2. Click **Launch an Eclipse Application** (creates a new Eclipse instance with the plugin loaded)
3. Configure credentials: `Window → Preferences → Checkmarx`

### Run Tests

```bash
# UI tests (requires Xvfb on Linux)
Xvfb -ac :99 -screen 0 1920x1080x16 &
mvn verify -Dtest.includes="**/ui/*.java" \
  -DCX_BASE_URI=<url> -DCX_TENANT=<tenant> \
  -DCX_APIKEY=<key> -DCX_TEST_SCAN=<scan-id>

# Unit tests only
mvn test -pl checkmarx-ast-eclipse-plugin-tests
```

---

## Coding Standards

- **Java 17** language level — use modern constructs (streams, lambdas, records where appropriate)
- **Logging:** Always use `CxLogger` (Eclipse ILog wrapper), never raw `System.out` or SLF4J directly in plugin code. SLF4J is available only for passing to the CLI wrapper internals.
- **UI thread safety:** All SWT widget updates must happen on the UI thread. Use `UISynchronizeImpl.asyncExec()` for background-to-UI transitions.
- **EventBus events:** Post events via `pluginEventBus.post(new PluginListenerDefinition(...))`. Subscribe with `@Subscribe`. Never call UI update methods directly from non-UI threads.
- **Constants:** Add all string literals used in UI or logic to `PluginConstants.java`. Never hardcode strings inline.
- **SWT layout:** Use `GridData`/`GridLayout` for all composites. Avoid fixed `widthHint` on combos that may contain variable-length content — use `SWT.FILL` with `grabExcessHorizontalSpace = true` instead.
- **Null safety:** Check `selectedItem.getResult()` and `selectedItem.getSeverity()` before accessing them — tree nodes may be group-level nodes with no attached result.

---

## Project Rules

- **All PRs target `main`** (or an integration branch when batching multiple bug fixes).
- **Branch naming:**
  - Bug fixes: `bug/AST-XXXXX`
  - Features: `feature/AST-XXXXX`
  - Documentation: `docs/AST-XXXXX`
  - Other: `other/AST-XXXXX`
- **Commit messages** must reference the Jira ticket: `Fix AST-XXXXX: <description>`
- **Never commit secrets.** Checkmarx credentials are injected via environment variables or Eclipse preferences at runtime — never hardcoded.
- **Wrapper version** is pinned in `ast-cli-java-wrapper.version`. Update this file and the JAR in `lib/` when upgrading the CLI wrapper.
- **Icons** must be placed in `checkmarx-ast-eclipse-plugin/icons/` and registered in `plugin.xml` if used as action images.
- **PR size:** Keep PRs focused on a single ticket. Use an integration branch to batch multiple related fixes before merging to main.

---

## Testing Strategy

### Test Types

| Type | Location | Runner | Purpose |
|------|----------|--------|---------|
| Unit | `unit/` | JUnit | Test logic in isolation (DataProvider, FilterState, PluginUtils) |
| UI (SWTBot) | `ui/` | SWTBot + JUnit | Test full plugin behavior inside a headless Eclipse instance |
| Integration | `integration/` | JUnit | Test authentication and API connectivity against a real Checkmarx One tenant |

### CI Triggers

- All tests run on **every PR to `main`** via GitHub Actions (`.github/workflows/ci.yml`)
- UI tests run on **Ubuntu** with **Xvfb** (virtual display)
- Integration tests require secrets: `CX_BASE_URI`, `CX_TENANT`, `CX_APIKEY`, `CX_TEST_SCAN`

### Coverage

- JaCoCo coverage reports generated per run
- Reports uploaded as GitHub Actions artifacts
- Coverage badge auto-generated via `cicirello/jacoco-badge-generator`

---

## External Integrations

| Integration | Purpose | How |
|-------------|---------|-----|
| **Checkmarx One Platform** | Fetch projects, branches, scans, results; submit triage | Via `ast-cli-java-wrapper` (wraps the Checkmarx CLI binary) |
| **JGit** | Detect current git branch to auto-select in branch combo | `RefsChangedListener` on local repo |
| **Eclipse Marketplace** | Plugin distribution and install | p2 update site published on release |
| **Codebashing** | Remediation lesson links per vulnerability | REST call to Checkmarx Codebashing API |

---

## Deployment

### Release Process

Releases are created via `.github/workflows/release.yml` (triggered manually or via `workflow_call`):

1. Input: `tag` (semver), `jira_ticket`, optional `rbranch` for dev releases
2. Tycho builds the p2 update site into `com.checkmarx.eclipse.site/target/`
3. Site artifact is published as a GitHub Release
4. Dev releases are cleaned up automatically before publishing a stable release

### Distribution

- **Eclipse Marketplace:** [checkmarx-ast-plugin](https://marketplace.eclipse.org/content/checkmarx-ast-plugin)
- **p2 Update Site:** published as a GitHub Release asset

### Install (End Users)

```
Help → Install New Software → Add repository URL (GitHub Release asset)
```

---

## Security & Access

- **API Key authentication:** Users configure a Checkmarx One API key in `Window → Preferences → Checkmarx`. The key is stored in the Eclipse secure preferences store.
- **Required roles:** `ast-scanner` (composite role) + `default-roles` IAM role on the Checkmarx One tenant.
- **No credentials in code:** All secrets are injected at runtime via preferences or environment variables (CI). Never commit API keys or tokens.
- **TLS:** All communication with Checkmarx One is HTTPS, enforced by the CLI wrapper.
- **Triage permissions:** Triage actions (severity/state changes) require the user's API key to have write permissions on the project.

---

## Logging

The plugin uses two logging mechanisms — use the right one for the right context:

| Logger | Class | Output | When to use |
|--------|-------|--------|-------------|
| `CxLogger` | `com.checkmarx.eclipse.utils.CxLogger` | Eclipse Error Log view + `.metadata/.log` | All plugin-level log messages |
| SLF4J | `org.slf4j.Logger` | No-op inside OSGi (dropped) | Only for passing to `CxWrapper` internals |

**Usage:**
```java
CxLogger.info("Loading results for scan: " + scanId);
CxLogger.error("Failed to fetch projects: " + e.getMessage(), e);
CxLogger.warning("Could not fetch platform states: " + e.getMessage());
```

**Viewing logs:**
- Eclipse IDE: `Window → Show View → Error Log`
- File: `<workspace>/.metadata/.log`

---

## Debugging Steps

### Plugin not loading

1. Check `Window → Show View → Error Log` for bundle activation errors
2. Verify Java 17 is set as the JRE: `Window → Preferences → Java → Installed JREs`
3. Confirm the plugin is enabled: `Help → About Eclipse → Installation Details`

### Authentication failures

1. Verify API key in `Window → Preferences → Checkmarx` — click **Authenticate**
2. Check Error Log for `CxLogger` messages containing `authentication` or `CxException`
3. Confirm the API key has `ast-scanner` + `default-roles` roles on the tenant

### No results / empty tree

1. Confirm project, branch, and scan ID are selected in the top combos
2. Check filter state — all severity filters may be disabled (toolbar toggle buttons)
3. Check Error Log for errors from `DataProvider.getResultsForScanId()`

### UI not updating after filter change

1. Confirm you are on a build that includes the AST-136035 fix
2. If the tree collapses entirely, check that `FILTER_CHANGED` calls `updateResultsTree(..., true)`

### Custom state dropdown overflow

1. Fixed in AST-137779 — ensure you are on a build that includes the `truncate()` fix in `ActionFilterStatePreference`

### Running UI tests locally (Linux)

```bash
Xvfb -ac :99 -screen 0 1920x1080x16 &
export DISPLAY=:99.0
mvn verify -Dtest.includes="**/ui/*.java" \
  -DCX_BASE_URI=$CX_BASE_URI \
  -DCX_TENANT=$CX_TENANT \
  -DCX_APIKEY=$CX_APIKEY \
  -DCX_TEST_SCAN=$CX_TEST_SCAN
```

---

## Known Issues

| Issue | Ticket | Status |
|-------|--------|--------|
| Severity filter clears description/attack vector panels | AST-136035 | Fixed |
| Severity filter collapses entire results tree | AST-136035 | Fixed |
| Authentication logs not routed to Eclipse Error Log | AST-136023 | Fixed |
| Custom State dropdown occupies entire screen | AST-137779 | Fixed |
| Scan ID combo overflows window on small screens | AST-136035 | Fixed |
| New scan ID not marked as latest in scan list after notification | AST-137779 | Open |

---

*Generated for AST-146800 · Checkmarx Integrations Team*
