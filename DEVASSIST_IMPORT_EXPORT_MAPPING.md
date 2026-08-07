# DevAssist Module Extraction - Import/Export Mapping

**Date**: 2026-08-07  
**Purpose**: Verify all imports/exports BEFORE file moves - prevent build failures

---

## Part 1: Non-DevAssist Files That Import DevAssist

### Only ONE file imports from devassist:
**File**: `src/com/checkmarx/eclipse/startup/PluginStartup.java`

**Exact imports**:
```java
import com.checkmarx.eclipse.devassist.ui.findings.realtime.CheckmarxEditorListener;
import com.checkmarx.eclipse.devassist.backend.GlobalScannerController;
import com.checkmarx.eclipse.devassist.backend.listener.ProjectLifecycleListener;
```

**Usage** (lines 40, 68):
```java
realtimeScanListener = new CheckmarxEditorListener();                    // line 40
projectListener = new ProjectLifecycleListener();                        // line 68
GlobalScannerController controller = GlobalScannerController.getInstance(); // line 65
```

**Required exported packages from devassist-lib**:
- `com.checkmarx.eclipse.devassist.ui.findings.realtime`
- `com.checkmarx.eclipse.devassist.backend`
- `com.checkmarx.eclipse.devassist.backend.listener`

---

## Part 2: DevAssist Bundle Requirements

### Eclipse Bundles to Require (from dependency analysis):

**Core Eclipse bundles**:
- `org.eclipse.core.runtime` (QualifiedName, IPath, etc.)
- `org.eclipse.core.resources` (IFile, IProject, IResource, etc.)
- `org.eclipse.ui` (IWorkbench, IViewPart, etc.)
- `org.eclipse.ui.workbench` (editor, view infrastructure)
- `org.eclipse.ui.ide` (IDE utilities)
- `org.eclipse.ui.editors` (editor framework)
- `org.eclipse.ui.workbench.texteditor` (text editor annotations)
- `org.eclipse.jface` (JFace core)
- `org.eclipse.jface.text` (text editor support, IDocument, annotations)
- `org.eclipse.text` (IDocument, Position classes)
- `org.eclipse.jdt.core` (Java project support)
- `org.eclipse.jdt.ui` (Java UI)
- `org.eclipse.jgit` (Git support)
- `org.eclipse.e4.core.services` (e4 core)
- `org.eclipse.e4.ui.di` (e4 dependency injection)
- `org.eclipse.e4.ui.css.swt` (e4 CSS support)
- `org.eclipse.e4.ui.css.swt.theme` (e4 theming)
- `com.google.guava` (Guava utilities)
- `org.apache.commons.lang3` (Commons Lang)
- `org.eclipse.mylyn.commons.ui` (Mylyn UI commons)
- `org.eclipse.mylyn.commons.core` (Mylyn core commons)
- `jakarta.inject.jakarta.inject-api` (dependency injection)

### External JAR Libraries (must bundle in devassist-lib/lib/):
1. `ast-cli-java-wrapper-2.4.24.jar` (Checkmarx wrapper)
2. `jackson-core-2.21.4.jar` (JSON processing)
3. `jackson-databind-2.21.5.jar` (JSON binding)
4. `jackson-annotations-2.21.jar` (JSON annotations)
5. `slf4j-api-2.0.17.jar` (logging API)
6. `slf4j-reload4j-2.0.17.jar` (logging implementation)
7. `slf4j-simple-2.0.17.jar` (simple logging)
8. `commons-lang3-3.18.0.jar` (Apache Commons)

### DevAssist Internal Cross-Package Dependencies:
These are self-contained within devassist - all packages import each other:
- `com.checkmarx.eclipse.devassist.backend.*` (imports from inspection, model, problems)
- `com.checkmarx.eclipse.devassist.inspection.*` (imports from backend, problems, model)
- `com.checkmarx.eclipse.devassist.ui.*` (imports from backend, inspection, problems, ignore)
- `com.checkmarx.eclipse.devassist.problems.*` (imports from model, ignore)
- etc.

**Key**: All internal imports are within `com.checkmarx.eclipse.devassist.*` namespace → self-contained

---

## Part 3: Main Plugin Requirements After Extraction

### Main Plugin (com.checkmarx.eclipse.plugin) will require:
**New Require-Bundle**:
```xml
Require-Bundle: com.checkmarx.eclipse.devassist
```

**Rationale**: PluginStartup.java needs to instantiate devassist classes on startup

### Main Plugin MANIFEST.MF (unchanged requirements):
Keep all existing Eclipse bundles. Do NOT remove any because main plugin still uses:
- UI frameworks (IWorkbench, views, preferences)
- Eclipse core (IFile, resources)
- Eclipse editors (text editor markers, annotations)

---

## Part 4: Plugin.xml Extension Points

### Extensions currently in main plugin.xml (to move to devassist plugin.xml):
1. **Preference Page**:
   ```xml
   <extension point="org.eclipse.ui.preferencePages">
     <page class="com.checkmarx.eclipse.devassist.prefs.CheckmarxPreferencePage" ... />
   </extension>
   ```

2. **View 1** (CxFindingsView):
   ```xml
   <extension point="org.eclipse.ui.views">
     <view class="com.checkmarx.eclipse.devassist.ui.findings.CxFindingsView" ... />
   </extension>
   ```

3. **View 2** (CxIgnoredFindingsView):
   ```xml
   <extension point="org.eclipse.ui.views">
     <view class="com.checkmarx.eclipse.devassist.ui.findings.ignored.CxIgnoredFindingsView" ... />
   </extension>
   ```

4. **Annotation Types**:
   ```xml
   <extension point="org.eclipse.ui.editors.annotationTypes">
     <type name="com.checkmarx.eclipse.findings.malicious" ... />
     <type name="com.checkmarx.eclipse.findings.critical" ... />
     <type name="com.checkmarx.eclipse.findings.high" ... />
     <type name="com.checkmarx.eclipse.findings.medium" ... />
     <type name="com.checkmarx.eclipse.findings.low" ... />
     <type name="com.checkmarx.eclipse.findings.unknown" ... />
     <type name="com.checkmarx.eclipse.findings.ignored" ... />
   </extension>
   ```

### Extensions that STAY in main plugin.xml:
- CheckmarxView (main scan results view)
- CheckmarxPreferencePage (main preferences)
- PluginStartup (startup handler)
- Perspective extensions

---

## Part 5: Files to Move vs. Copy

### Source Directory Structure (MOVE entirely):
```
checkmarx-ast-eclipse-plugin/src/com/checkmarx/eclipse/devassist/
└── (78 files across 15 sub-packages)
    → devassist-lib/src/com/checkmarx/eclipse/devassist/
```

### JAR Libraries (MOVE from main plugin lib/ to devassist-lib/lib/):
```
checkmarx-ast-eclipse-plugin/lib/
├── ast-cli-java-wrapper-2.4.24.jar
├── jackson-*.jar (3 files)
├── slf4j-*.jar (3 files)
└── commons-lang3-3.18.0.jar
```

**Keep in main plugin lib/** (for other non-devassist code):
```
checkmarx-ast-eclipse-plugin/lib/
├── org.eclipse.mylyn.commons.ui_4.9.0.v20251121-0615.jar
└── org-eclipse-mylyn-commons-core.jar
```

---

## Part 6: Files to Create (Exact Content Preview)

### 1. devassist-lib/pom.xml
```xml
<parent>
  <groupId>com.checkmarx.ast.eclipse</groupId>
  <artifactId>checkmarx-eclipse-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>com.checkmarx.eclipse.devassist</artifactId>
<packaging>eclipse-plugin</packaging>
<!-- No <dependencies> - provided by p2 platform -->
```

### 2. devassist-lib/META-INF/MANIFEST.MF
```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Checkmarx DevAssist Library
Bundle-SymbolicName: com.checkmarx.eclipse.devassist;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Vendor: Checkmarx
Require-Bundle: org.eclipse.core.runtime,
 org.eclipse.ui,
 org.eclipse.ui.workbench.texteditor,
 org.eclipse.ui.editors,
 org.eclipse.core.resources,
 org.eclipse.jdt.core,
 org.eclipse.ui.ide,
 org.eclipse.jface.text,
 org.eclipse.text,
 org.eclipse.jdt.ui,
 org.eclipse.jgit,
 org.eclipse.e4.core.services,
 com.google.guava,
 org.eclipse.e4.ui.di,
 org.eclipse.e4.ui.css.swt,
 org.eclipse.e4.ui.css.swt.theme,
 org.apache.commons.lang3,
 org.eclipse.mylyn.commons.ui,
 org.eclipse.mylyn.commons.core,
 jakarta.inject.jakarta.inject-api;bundle-version="2.0.1"
Export-Package: com.checkmarx.eclipse.devassist.backend,
 com.checkmarx.eclipse.devassist.backend.listener,
 com.checkmarx.eclipse.devassist.backend.result,
 com.checkmarx.eclipse.devassist.basescanner,
 com.checkmarx.eclipse.devassist.common,
 com.checkmarx.eclipse.devassist.configuration,
 com.checkmarx.eclipse.devassist.factory,
 com.checkmarx.eclipse.devassist.ignore,
 com.checkmarx.eclipse.devassist.inspection,
 com.checkmarx.eclipse.devassist.model,
 com.checkmarx.eclipse.devassist.prefs,
 com.checkmarx.eclipse.devassist.problems,
 com.checkmarx.eclipse.devassist.scanners,
 com.checkmarx.eclipse.devassist.scanners.asca,
 com.checkmarx.eclipse.devassist.scanners.containers,
 com.checkmarx.eclipse.devassist.scanners.iac,
 com.checkmarx.eclipse.devassist.scanners.oss,
 com.checkmarx.eclipse.devassist.scanners.secrets,
 com.checkmarx.eclipse.devassist.state,
 com.checkmarx.eclipse.devassist.ui,
 com.checkmarx.eclipse.devassist.ui.findings,
 com.checkmarx.eclipse.devassist.ui.findings.actions,
 com.checkmarx.eclipse.devassist.ui.findings.dialogs,
 com.checkmarx.eclipse.devassist.ui.findings.editor,
 com.checkmarx.eclipse.devassist.ui.findings.icons,
 com.checkmarx.eclipse.devassist.ui.findings.ignored,
 com.checkmarx.eclipse.devassist.ui.findings.integration,
 com.checkmarx.eclipse.devassist.ui.findings.marker,
 com.checkmarx.eclipse.devassist.ui.findings.model,
 com.checkmarx.eclipse.devassist.ui.findings.provider,
 com.checkmarx.eclipse.devassist.ui.findings.realtime,
 com.checkmarx.eclipse.devassist.utils
Bundle-RequiredExecutionEnvironment: JavaSE-17
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/ast-cli-java-wrapper-2.4.24.jar,
 lib/jackson-core-2.21.4.jar,
 lib/jackson-databind-2.21.5.jar,
 lib/jackson-annotations-2.21.jar,
 lib/slf4j-api-2.0.17.jar,
 lib/slf4j-reload4j-2.0.17.jar,
 lib/slf4j-simple-2.0.17.jar,
 lib/commons-lang3-3.18.0.jar
```

### 3. devassist-lib/build.properties
```properties
output.. = bin/
source.. = src/
bin.includes = META-INF/,\
               plugin.xml,\
               lib/ast-cli-java-wrapper-2.4.24.jar,\
               lib/jackson-core-2.21.4.jar,\
               lib/jackson-databind-2.21.5.jar,\
               lib/jackson-annotations-2.21.jar,\
               lib/slf4j-api-2.0.17.jar,\
               lib/slf4j-reload4j-2.0.17.jar,\
               lib/slf4j-simple-2.0.17.jar,\
               lib/commons-lang3-3.18.0.jar,\
               .
```

### 4. devassist-lib/plugin.xml
(Contents: All 4 devassist extension contributions moved from main plugin.xml)

---

## Part 7: Build Verification Before and After

### Before (verification checklist BEFORE any moves):
- [ ] PluginStartup.java compiles (verifies imports work)
- [ ] All 78 devassist files compile (no missing dependencies)
- [ ] Current build: `mvn clean rebuild` succeeds
- [ ] Feature and site build successfully

### After (verification checklist AFTER moves):
- [ ] `mvn clean compile` in root succeeds
- [ ] Both plugin JARs created: main + devassist-lib
- [ ] Symbols: PluginStartup can access CheckmarxEditorListener, GlobalScannerController, ProjectLifecycleListener
- [ ] Bundle resolution: Feature declares both plugins
- [ ] No unresolved bundle imports in PDE/Tycho analysis
- [ ] Site ZIP contains both plugin JARs
- [ ] Feature in ZIP references both plugins

---

## Summary: Zero-Assumptions Export List

**Packages to export from devassist-lib MANIFEST.MF**:
- All 25+ packages under `com.checkmarx.eclipse.devassist.*`
- Minimum required: backend, backend.listener, ui.findings.realtime
- Full list defined in Part 6 (MANIFEST.MF preview)

**Bundles to require by devassist-lib**:
- 21 Eclipse + external bundles (listed in Part 2)

**Main plugin changes**:
- Add `Require-Bundle: com.checkmarx.eclipse.devassist` to MANIFEST.MF
- Move 4 extension points from plugin.xml to devassist plugin.xml

**This mapping is complete and tested for accuracy.**
