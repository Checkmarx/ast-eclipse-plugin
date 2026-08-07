# DevAssist Module Extraction - Phase 1 Plan

## Objective
Extract `com.checkmarx.eclipse.devassist` code into a separate OSGi bundle module while maintaining single unified distribution (one feature, one ZIP). All 78 devassist Java files remain in `com.checkmarx.eclipse.devassist.*` package namespace.

## Current State (Verified Facts)
- Devassist: 78 Java files across 15 sub-packages (backend, inspection, ui, problems, scanners, ignore, etc.)
- Coupling: Only 6 non-devassist classes import from devassist:
  - `properties/PreferencesPage.java` (2 imports)
  - `properties/CxPreferencesDialogSizing.java` (1 import)
  - `startup/PluginStartup.java` (3 imports)
- Resources: All icons/assets at plugin root; no resources inside devassist/
- Plugin extensions: 4 devassist items in plugin.xml (preference page, 2 views, perspective extension)

## Phase 1 Deliverables
1. New OSGi bundle: `com.checkmarx.eclipse.devassist/`
2. Single distribution: `com.checkmarx.eclipse.feature` includes both plugins
3. No code changes: imports remain valid, namespace unchanged
4. Build succeeds: Maven + Tycho compilation/packaging succeeds

## Files to Create
1. `devassist-lib/pom.xml` — Maven bundle configuration
2. `devassist-lib/META-INF/MANIFEST.MF` — OSGi bundle metadata with exports/imports
3. `devassist-lib/plugin.xml` — Eclipse extension point declarations (prefs, views, annotation types)
4. `devassist-lib/build.properties` — Tycho build includes (source, JAR libraries)

## Files to Modify
1. **Root `pom.xml`**: Add `<module>devassist-lib</module>` to module list
2. **Main plugin MANIFEST.MF**: Add `Require-Bundle: com.checkmarx.eclipse.devassist` (compatibility package)
3. **Main plugin `plugin.xml`**: Move 4 devassist extensions to new plugin.xml
4. **Main plugin `build.properties`**: Remove JAR files moved to devassist-lib
5. **Feature `feature.xml`**: Add `<plugin id="com.checkmarx.eclipse.devassist" ... />`

## Files to Move
- **Source**: All 78 files from `checkmarx-ast-eclipse-plugin/src/com/checkmarx/eclipse/devassist/` 
- **Destination**: `devassist-lib/src/com/checkmarx/eclipse/devassist/`
- **Preserve**: Full directory structure (backend/, ui/, scanners/, etc.)

## External Dependencies (JAR Libraries)
Bundle in `devassist-lib/lib/`:
- ast-cli-java-wrapper-2.4.24.jar
- jackson-core-2.21.4.jar
- jackson-databind-2.21.5.jar
- jackson-annotations-2.21.jar
- slf4j-api-2.0.17.jar
- slf4j-reload4j-2.0.17.jar
- slf4j-simple-2.0.17.jar
- commons-lang3-3.18.0.jar

(Remove from main plugin lib/)

## OSGi Bundle Configuration Details
- **Symbolic Name**: `com.checkmarx.eclipse.devassist`
- **Require-Bundle**: org.eclipse.core.runtime, org.eclipse.ui, org.eclipse.jface.text, org.eclipse.text, org.eclipse.jdt.core, org.eclipse.ui.editors, org.eclipse.core.resources, org.eclipse.jgit, org.eclipse.e4.core.services, com.google.guava, etc.
- **Export-Package**: `com.checkmarx.eclipse.devassist.*` (all public packages)
- **Lazy Activation**: `Bundle-ActivationPolicy: lazy`

## Build Verification Checklist
- [ ] `mvn clean compile` succeeds (all 6 importing classes resolve devassist symbols)
- [ ] `mvn clean rebuild` creates both plugin JARs
- [ ] Feature JAR created with both plugins listed
- [ ] Update site generated successfully
- [ ] Main plugin MANIFEST properly requires devassist bundle
- [ ] No unresolved imports/exports in bundle analysis

## Blocked/At-Risk Items
None identified. Coupling is minimal and directional (main→devassist, no reverse).

## Effort Estimate
~3 hours: File moves, pom/manifest updates, build verification. No logic changes.

## Next Steps (Post-Review)
1. User approves plan
2. Create pom.xml with exact dependency declarations
3. Create MANIFEST.MF with tested require-bundle list
4. Move files via git mv (preserves history)
5. Execute build verification checklist
6. Commit and create PR
