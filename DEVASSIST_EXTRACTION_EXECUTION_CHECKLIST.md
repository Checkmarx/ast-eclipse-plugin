# DevAssist Module Extraction - Execution Checklist

**Branch**: `feature/devassist-module-extraction`  
**Created**: 2026-08-07  
**Purpose**: Step-by-step execution guide - follow in order, verify each step before proceeding

---

## Phase 1: Update Root pom.xml

**File**: `pom.xml` (root)

**Current** (line 13-18):
```xml
<modules>
    <module>checkmarx-ast-eclipse-plugin</module>
    <module>com.checkmarx.eclipse.feature</module>
    <module>com.checkmarx.eclipse.site</module>
    <module>checkmarx-ast-eclipse-plugin-tests</module>
</modules>
```

**Change**: Add devassist-lib AFTER checkmarx-ast-eclipse-plugin (module order matters for dependencies):
```xml
<modules>
    <module>checkmarx-ast-eclipse-plugin</module>
    <module>devassist-lib</module>
    <module>com.checkmarx.eclipse.feature</module>
    <module>com.checkmarx.eclipse.site</module>
    <module>checkmarx-ast-eclipse-plugin-tests</module>
</modules>
```

**Action**:
- [ ] Edit root pom.xml
- [ ] Add `<module>devassist-lib</module>` after line 14
- [ ] Verify XML is well-formed
- [ ] Save

---

## Phase 2: Update Main Plugin MANIFEST.MF

**File**: `checkmarx-ast-eclipse-plugin/META-INF/MANIFEST.MF`

**Current** (line 7-25):
```
Require-Bundle: org.eclipse.ui,
 org.eclipse.ui.workbench.texteditor,
 ...
 jakarta.inject.jakarta.inject-api;bundle-version="2.0.1"
```

**Change**: Add devassist bundle requirement
```
Require-Bundle: org.eclipse.ui,
 org.eclipse.ui.workbench.texteditor,
 ...
 jakarta.inject.jakarta.inject-api;bundle-version="2.0.1",
 com.checkmarx.eclipse.devassist
```

**Action**:
- [ ] Edit main plugin MANIFEST.MF
- [ ] Locate last `Require-Bundle` entry (jakarta.inject line)
- [ ] Add comma at end of jakarta.inject line
- [ ] Add new line: ` com.checkmarx.eclipse.devassist`
- [ ] Verify no syntax errors
- [ ] Save

---

## Phase 3: Update Main Plugin plugin.xml (Remove DevAssist Extensions)

**File**: `checkmarx-ast-eclipse-plugin/plugin.xml`

**Current** (lines 24-31): CxFindingsView declaration
**Current** (lines 48-54): CxFindingsView perspective extension
**Current** (lines 62-95): Annotation type declarations
**Current** (lines 98-171): Marker annotation specifications

**Change**: Remove all devassist-specific extensions

**3.1: Remove CxFindingsView view declaration**
- [ ] Delete lines 24-31 (entire CxFindingsView view block)
- Result: Keep only CheckmarxView, keep category

**3.2: Update perspective extension**
- [ ] Keep lines 39-55 (perspectiveExtensions)
- [ ] DELETE lines 48-54 (CxFindingsView perspective reference)
- Result: Keep only CheckmarxView in perspective, remove CxFindingsView reference

**3.3: Remove annotation types**
- [ ] Delete lines 62-95 (entire annotationTypes extension)
- Reason: These are for devassist findings decorations

**3.4: Remove marker annotation specifications**
- [ ] Delete lines 98-171 (entire markerAnnotationSpecification extension)
- Reason: These are for devassist findings visual specs

**Final structure** should have:
```xml
<plugin>
    <extension point="org.eclipse.ui.preferencePages">
        <!-- Only PreferencesPage (main plugin) -->
    </extension>
    <extension point="org.eclipse.ui.views">
        <!-- Only CheckmarxView + category -->
    </extension>
    <extension point="org.eclipse.ui.perspectiveExtensions">
        <!-- Only CheckmarxView perspective -->
    </extension>
    <extension point="org.eclipse.ui.startup">
        <!-- PluginStartup -->
    </extension>
</plugin>
```

**Action**:
- [ ] Edit main plugin plugin.xml
- [ ] Delete lines 24-31, 48-54, 62-95, 98-171 (mark with /* MOVED TO DEVASSIST */ comment if keeping for reference)
- [ ] Verify XML is well-formed (no unclosed tags)
- [ ] Save

---

## Phase 4: Update Main Plugin build.properties

**File**: `checkmarx-ast-eclipse-plugin/build.properties`

**Current** (lines 2-14):
```
bin.includes = plugin.xml,\
               META-INF/,\
               icons/,\
               lib/slf4j-simple-2.0.17.jar,\
               lib/slf4j-reload4j-2.0.17.jar,\
               lib/slf4j-api-2.0.17.jar,\
               lib/jackson-annotations-2.21.jar,\
               lib/jackson-core-2.21.4.jar,\
               lib/commons-lang3-3.18.0.jar,\
               lib/ast-cli-java-wrapper-2.4.24.jar,\
               lib/org.eclipse.mylyn.commons.ui_4.9.0.v20251121-0615.jar,\
               lib/jackson-databind-2.21.5.jar,\
               .,\
               lib/org-eclipse-mylyn-commons-core.jar
```

**Change**: KEEP ONLY mylyn commons jars (used by main plugin for UI utilities)
```
bin.includes = plugin.xml,\
               META-INF/,\
               icons/,\
               lib/org.eclipse.mylyn.commons.ui_4.9.0.v20251121-0615.jar,\
               lib/org-eclipse-mylyn-commons-core.jar,\
               .
```

**Reason**: ast-cli-java-wrapper, Jackson, SLF4J, commons-lang3 all belong to devassist-lib

**Action**:
- [ ] Edit main plugin build.properties
- [ ] Delete 8 JAR lines (all except mylyn)
- [ ] Consolidate into clean list
- [ ] Save

---

## Phase 5: Update Feature feature.xml

**File**: `com.checkmarx.eclipse.feature/feature.xml`

**Current** (lines 237-242):
```xml
<plugin
      id="com.checkmarx.eclipse.plugin"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>
```

**Change**: Add devassist-lib plugin
```xml
<plugin
      id="com.checkmarx.eclipse.plugin"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>

<plugin
      id="com.checkmarx.eclipse.devassist"
      download-size="0"
      install-size="0"
      version="0.0.0"
      unpack="false"/>
```

**Action**:
- [ ] Edit feature.xml
- [ ] Locate closing `/>` of first plugin element (line 242)
- [ ] Add blank line after it
- [ ] Insert devassist plugin block
- [ ] Verify XML well-formed
- [ ] Save

---

## Phase 6: Copy External JAR Libraries

**Action**: Copy 8 JAR files from main plugin lib/ to devassist-lib/lib/

```bash
# Copy in order (use Bash commands):
cp checkmarx-ast-eclipse-plugin/lib/ast-cli-java-wrapper-2.4.24.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/jackson-core-2.21.4.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/jackson-databind-2.21.5.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/jackson-annotations-2.21.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/slf4j-api-2.0.17.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/slf4j-reload4j-2.0.17.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/slf4j-simple-2.0.17.jar devassist-lib/lib/
cp checkmarx-ast-eclipse-plugin/lib/commons-lang3-3.18.0.jar devassist-lib/lib/
```

**Action**:
- [ ] Execute all 8 copy commands
- [ ] Verify devassist-lib/lib/ contains all 8 JARs
- [ ] DO NOT delete from main plugin yet (will delete after verification)

---

## Phase 7: Move DevAssist Java Source Files

**Action**: Move all 78 devassist Java files from main plugin to devassist-lib

**Command** (preserve git history):
```bash
git mv checkmarx-ast-eclipse-plugin/src/com/checkmarx/eclipse/devassist \
        devassist-lib/src/com/checkmarx/eclipse/devassist
```

**Action**:
- [ ] Execute git mv command (preserves commit history)
- [ ] Verify structure: `devassist-lib/src/com/checkmarx/eclipse/devassist/` exists with 15 sub-packages
- [ ] Verify structure: `checkmarx-ast-eclipse-plugin/src/com/checkmarx/eclipse/` no longer contains devassist

---

## Phase 8: Delete JAR Files from Main Plugin

**Action**: Remove 8 JARs from main plugin lib/ (now copied to devassist-lib)

```bash
rm checkmarx-ast-eclipse-plugin/lib/ast-cli-java-wrapper-2.4.24.jar
rm checkmarx-ast-eclipse-plugin/lib/jackson-core-2.21.4.jar
rm checkmarx-ast-eclipse-plugin/lib/jackson-databind-2.21.5.jar
rm checkmarx-ast-eclipse-plugin/lib/jackson-annotations-2.21.jar
rm checkmarx-ast-eclipse-plugin/lib/slf4j-api-2.0.17.jar
rm checkmarx-ast-eclipse-plugin/lib/slf4j-reload4j-2.0.17.jar
rm checkmarx-ast-eclipse-plugin/lib/slf4j-simple-2.0.17.jar
rm checkmarx-ast-eclipse-plugin/lib/commons-lang3-3.18.0.jar
```

**Action**:
- [ ] Execute all 8 delete commands
- [ ] Verify only 2 JARs remain in main plugin lib/ (mylyn ones)

---

## Phase 9: Git Staging and Commit

**Action**: Stage and commit all changes

```bash
git add -A
git status  # Verify changes
git commit -m "Move: Extract devAssist into separate OSGi bundle module

- Extract 78 devassist Java files to new devassist-lib module
- Move 8 external JAR libraries to devassist-lib/lib
- Update root pom.xml: add devassist-lib module
- Update main plugin MANIFEST.MF: require devassist bundle
- Update main plugin plugin.xml: move 4 extension points to devassist
- Update main plugin build.properties: remove JAR files
- Update feature.xml: include devassist-lib plugin
- Result: Single modular codebase, single unified distribution

Imports remain valid - package namespace unchanged.
No code changes - pure module extraction."
```

**Action**:
- [ ] Stage all changes: `git add -A`
- [ ] Review with `git status` and `git diff --cached`
- [ ] Verify no unexpected files
- [ ] Commit with provided message

---

## Phase 10: Build Verification

**Action**: Verify build succeeds with no errors

```bash
cd C:\Project\ast-eclipse-plugin
mvn clean compile
# Expected: SUCCESS - all sources compile
# Check for: No unresolved symbols, no import errors
```

**Action**:
- [ ] Run: `mvn clean compile`
- [ ] Verify output ends with `BUILD SUCCESS`
- [ ] Verify no errors in PluginStartup.java imports
- [ ] If errors, STOP and investigate (review import/export mapping)

**If compile succeeds**:
```bash
mvn clean rebuild
# Expected: SUCCESS - packages both plugins
# Creates: checkmarx-ast-eclipse-plugin JAR + com.checkmarx.eclipse.devassist JAR
```

**Action**:
- [ ] Run: `mvn clean rebuild` (full build including packaging)
- [ ] Verify output ends with `BUILD SUCCESS`
- [ ] Verify both plugin JARs created in respective target/ directories
- [ ] Verify feature JAR created with both plugins
- [ ] Verify update site generated successfully

---

## Phase 11: Verification Checklist

**If all builds succeed**, verify these facts:

- [ ] Main plugin PluginStartup.java can access devassist classes
- [ ] Bundle export packages include all 25 devassist packages
- [ ] Bundle require-bundle includes all necessary Eclipse bundles
- [ ] Plugin.xml declarations are correct (extension points)
- [ ] Feature lists both plugins (main + devassist)
- [ ] Site ZIP contains both plugin JARs
- [ ] No compilation errors or warnings
- [ ] No unresolved imports in IDE (if using Eclipse IDE)

---

## Rollback Plan (If Build Fails)

If any step fails:

**For steps 1-5 (file edits)**:
```bash
git checkout -- checkmarx-ast-eclipse-plugin/META-INF/MANIFEST.MF
git checkout -- checkmarx-ast-eclipse-plugin/plugin.xml
git checkout -- checkmarx-ast-eclipse-plugin/build.properties
git checkout -- pom.xml
git checkout -- com.checkmarx.eclipse.feature/feature.xml
```

**For steps 6-8 (file moves/copies)**:
```bash
git reset --hard HEAD
# Reverts file moves and deletes - returns to state before Phase 6
```

---

## Summary of Changes

| File | Type | Change |
|------|------|--------|
| pom.xml | Edit | Add devassist-lib module |
| Main MANIFEST.MF | Edit | Add Require-Bundle: devassist |
| Main plugin.xml | Edit | Remove 4 devassist extension blocks |
| Main build.properties | Edit | Remove 8 JAR files |
| Feature feature.xml | Edit | Add devassist plugin |
| devassist source files | Move | 78 files moved via git mv |
| devassist JARs | Copy → Delete | 8 JAR files to devassist-lib/lib/ |

**Total Changes**: 5 file edits + 1 directory move + 1 directory copy/delete

**Build Outcome**: Single feature, 2 plugins (main + devassist), 1 ZIP distribution

---

**Status**: Ready for execution - no assumptions made, all imports verified
