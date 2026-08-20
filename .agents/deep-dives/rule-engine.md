# WildFly Glow Discovery Rule Engine

## Pipeline Overview

### Stage 1 — Build the Rule Model (`Utils.buildMapping`)

`Utils.buildMapping()` iterates every `Layer` object (populated from `layer-spec.xml` `<props>`) and parses each `org.wildfly.rule.*` property into the appropriate lookup structure inside a `LayerMapping` instance. Annotation rules go into `annotations` / `annotationFieldValues` / `annotatedTypes` maps keyed by annotation class name. Java-type rules go into `constantPoolClassInfos` keyed by class or package name. File-based rules (`xml-path`, `expected-file`, `not-expected-file`, `properties-file-match`) remain as raw properties on the `Layer` and are evaluated later during deployment inspection. Add-on metadata is assembled into `AddOn` objects grouped by family. Conditional rules (`hidden-if`, `no-configuration-if`) are stored in dedicated maps from `Layer` to the original property key. Wildcard values (containing `*`) are converted to regex patterns via `Utils.escapePattern()`.

### Stage 2 — Scan Deployment Content (`DeploymentScanner`)

`DeploymentScanner` opens the deployment archive (WAR/JAR/EAR/RAR/SAR) or exploded directory and runs three passes. First, `scanAnnotations()` builds a Jandex index of all classes and checks each annotation instance against the `annotations`, `annotationFieldValues`, and `annotatedTypes` maps from the `LayerMapping`. Second, `scanTypesAndChildren()` walks every `.class` file, parses its constant pool, field descriptors, method descriptors, and local variable tables to extract referenced Java types, then calls `lookup()` to match each type against `constantPoolClassInfos`. Nested archives (e.g., JARs inside `WEB-INF/lib/`) are scanned recursively via child `DeploymentScanner` instances. Third, `inspectDeployment()` evaluates file-based rules — it uses `DeploymentFileRuleInspector` to resolve file paths (literal or glob) within the archive, then applies XPath queries (`xml-path`), property key/value matching (`properties-file-match`), file existence checks (`expected-file`), and file absence checks (`not-expected-file`). Conditional rules (`hidden-if`, `no-configuration-if`) are also evaluated here: if their file-based condition matches, the layer is banned or its configuration is cleared.

### Stage 3 — Match and Resolve Layers (`GlowSession.scan`)

`GlowSession.scan()` orchestrates the full pipeline. It resolves feature packs, loads all available layers via `Utils.getAllLayers()`, builds the `LayerMapping`, then runs `DeploymentScanner` against each input binary. After scanning, it processes manually specified layers, resolves the default base layer, computes transitive dependencies, and enters the add-on resolution phase. Add-ons are either auto-suggested (added to `possibleAddOns`) or force-included (if the user explicitly enabled them). Layers with `inclusion-mode` set to `all-dependencies` or `only:<layers>` are included when their dependency conditions are met. Profile-based layer swapping (e.g., `ha` profile replaces a non-HA layer with its HA variant) is applied next. The result is a `ScanResults` containing discovered layers, suggested add-ons, suggested profiles, excluded layers, and identified errors.

## Rule Types Reference

All property names are prefixed with `org.wildfly.rule.`. Rules with a `-suffix` accept an arbitrary suffix to allow multiple instances on the same layer (e.g., `xml-path-a`, `xml-path-b`).

| Property | Matches Against | `RULE` Enum Value | Example |
|---|---|---|---|
| `annotations` | Annotation class names found via Jandex index. Value is a comma-separated list of fully-qualified names or package prefixes. Supports `*` wildcards. | `ANNOTATION` | `jaxrs`: `value="jakarta.ws.rs,jakarta.ws.rs.core"` |
| `class` | Java types referenced in constant pool, field/method descriptors, local variable tables. Value is comma-separated class/package names. Supports `*` wildcards. | `JAVA_TYPE` | `jaxrs`: `value="jakarta.ws.rs,jakarta.ws.rs.core"` |
| `annotation.field.value-<suffix>` | Annotation instances where a specific field has a specific value. Format: `<annotation>,<field>=<value>`. Supports `*` in value. | `ANNOTATION_VALUE` | `value="org.wildfly.glow.test.FieldValue,prop=foo*"` |
| `annotated.type-<suffix>` | Field annotations where the annotated field has a specific Java type, optionally with annotation field constraints. Format: `<type>,<annotation>[field=value]`. | `ANNOTATED_TYPE` | `value="java.lang.Object,com.example.MyAnnotation[prop=coco]"` |
| `xml-path-<suffix>` | XML files in the deployment matched by XPath. Format: `<file-path>,<xpath-expr>[,<expected-value>]`. File path supports `[path1,path2]` for multiple files and `*` wildcards. | `XML_PATH` | `jaxrs`: `value="/WEB-INF/web.xml,/web-app/servlet/servlet-class,jakarta.ws.rs.core.Application"` |
| `expected-file-<suffix>` | File or directory existence in the deployment. Matches if the path exists. Supports `*` wildcards and `[path1,path2]` multi-path syntax. | `EXPECTED_FILE` | `jsf`: `value="/WEB-INF/faces-config.xml"` |
| `not-expected-file-<suffix>` | File absence. Layer is included when **none** of the specified paths exist. Supports `*` wildcards and `[path1,path2]` syntax. | `NOT_EXPECTED_FILE` | `value="/META-INF/ne-one-no-wildcard.txt"` |
| `properties-file-match-<suffix>` | Properties files where a key (and optionally a value) matches. Format: `<file-path>,<key-pattern>[,<value-pattern>]`. Supports `*` wildcards everywhere. | `PROPERTIES_FILE` | `value="/META-INF/app.properties,testA-key,valueA"` |
| `bring-datasource` | JNDI name of a datasource this layer provides. When a `@Resource` injection or JNDI lookup references this name, the layer is included. | `BRING_DATASOURCE` | `value="java:jboss/datasources/ExampleDS"` |
| `profile-<name>` | Execution profile support. Value is the name of the layer that replaces this one when the named profile is active (e.g., `ha`). | `PROFILE_INCLUDED` / `PROFILE_EXCLUDED` | `prop name="org.wildfly.rule.profile-ha" value="ejb-dist-cache"` |
| `inclusion-mode` | Dependency-based auto-inclusion. `all-dependencies` includes the layer when all its declared dependencies are already discovered. `only:<layer1>,<layer2>` includes it when the named layers are present. | `ADD_ON_REQUIRED_DEPENDENCIES_FOUND` | `value="all-dependencies"` |
| `kind` | Layer classification. `default-base-layer` marks the base layer (always included). `metadata-only` marks layers that carry metadata but no subsystem config. | `BASE_LAYER` | `value="default-base-layer"` |

## Add-on System

Add-ons represent optional features that Glow **suggests** to the user rather than automatically including. They are organized into **families** — groups of mutually exclusive alternatives that serve the same purpose. A layer becomes an add-on member by declaring the following properties:

### Properties

- **`add-on`**: `value="<family>,<name>"` — Registers this layer as an add-on named `<name>` in family `<family>`. If `<name>` ends with `:default`, it is marked as the default choice for the family.

- **`add-on-depends-on`**: Controls when the add-on is suggested. Three modes:
  - `all-dependencies` — Suggest when all of the layer's declared `<dependencies>` are already discovered.
  - `only:<layer1>,<layer2>` — Suggest when the named layers are present (a subset of dependencies).
  - `none` — Always suggest when the add-on system is active; the layer is always included if the add-on is enabled.

- **`add-on-cardinality`**: `value="<n>"` — Maximum number of add-ons from this family that can be enabled simultaneously. Typically `1` for mutually exclusive alternatives.

- **`add-on-description`**: Human-readable text describing what the add-on provides.

- **`add-on-fix-<id>`**: `value="<description>,<content>"` — Declares that enabling this add-on fixes the error identified by `<id>`. Used by the error identification system to suggest add-ons as solutions.

- **`expect-add-on-family`**: Set on the **parent** layer (not the add-on layers). Value is the family name. Tells Glow that after discovering this layer, the user should choose an add-on from the named family.

### Messaging Example

The messaging subsystem demonstrates the full add-on lifecycle:

1. **`messaging-activemq`** (parent layer) declares discovery rules and expects an add-on:
   ```xml
   <prop name="org.wildfly.rule.class" value="jakarta.jms"/>
   <prop name="org.wildfly.rule.annotations" value="jakarta.jms"/>
   <prop name="org.wildfly.rule.expect-add-on-family" value="messaging"/>
   ```

2. **`embedded-activemq`** registers as an add-on in the `messaging` family with cardinality 1:
   ```xml
   <prop name="org.wildfly.rule.add-on" value="messaging,embedded-activemq"/>
   <prop name="org.wildfly.rule.add-on-depends-on" value="only:messaging-activemq"/>
   <prop name="org.wildfly.rule.add-on-cardinality" value="1"/>
   ```

3. **`remote-activemq`** registers as the other option in the same family:
   ```xml
   <prop name="org.wildfly.rule.add-on" value="messaging,remote-activemq"/>
   <prop name="org.wildfly.rule.add-on-depends-on" value="only:messaging-activemq"/>
   ```

**Resolution flow**: When Glow scans a deployment using `jakarta.jms` types, it discovers `messaging-activemq`. Because that layer has `expect-add-on-family=messaging`, Glow looks for add-ons in the `messaging` family. Both `embedded-activemq` and `remote-activemq` have `add-on-depends-on=only:messaging-activemq`, and that dependency is satisfied. Both are added to `possibleAddOns` (suggested to the user). The cardinality of `1` means only one can be enabled. If the user enables `embedded-activemq`, its layer (and its dependencies like `cdi`, `remoting`, `undertow`) are added to the provisioning set.

## Conditional Rules

Conditional rules modify a layer's behavior based on deployment content. They reuse the same file-matching syntax as `expected-file`, `xml-path`, etc., but instead of discovering a layer, they alter a layer that has **already** been discovered or is being evaluated.

### `hidden-if`

**Syntax**: `org.wildfly.rule.hidden-if-<suffix>=<file-rule>`

If the file-based condition matches, the layer is **banned** (`layer.setBanned(true)`). A banned layer is excluded from the final discovered set even if other rules matched it. This is used to hide a layer when deployment content indicates it is not needed.

The `<file-rule>` follows the same format as `expected-file` or `xml-path` — the suffix after `hidden-if` determines which file-rule type is evaluated. For example, `hidden-if-expected-file` checks file existence; `hidden-if-xml-path` checks XPath matches.

### `no-configuration-if`

**Syntax**: `org.wildfly.rule.no-configuration-if-<suffix>=<file-rule>`

If the file-based condition matches, the layer's configuration documents are **cleared** (`layer.getConfiguration().clear()`). The layer itself is still included, but no configuration snippets are applied. This is used when deployment content indicates the subsystem is needed but should use default configuration rather than Glow-generated configuration.

### Evaluation

Both conditions are evaluated during `DeploymentScanner.inspectDeployment()`. The condition property key starts with `hidden-if` or `no-configuration-if`, followed by the actual rule type. `LayerMapping.cleanupKey()` strips the condition prefix to extract the underlying rule (e.g., `hidden-if-expected-file=/WEB-INF/foo.xml` becomes `expected-file=/WEB-INF/foo.xml`). The underlying rule is matched against the deployment; if it matches, the condition fires.

## How to Add Rules for a New Subsystem

Feature pack authors add discovery rules by editing `layer-spec.xml` in their Galleon feature pack. No changes to Glow core are required — Glow reads rules dynamically from layer properties at scan time.

### Checklist

1. **Identify the layer's `layer-spec.xml`** in your feature pack under `src/main/resources/layers/standalone/<layer-name>/layer-spec.xml`.

2. **Add a `<props>` block** inside `<layer-spec>` with `org.wildfly.rule.*` properties:

   - Add `org.wildfly.rule.annotations` with comma-separated annotation packages/classes that indicate usage of your subsystem (e.g., `jakarta.persistence`).
   - Add `org.wildfly.rule.class` with comma-separated class/package names from your subsystem's API that applications import (e.g., `jakarta.persistence`).
   - If your subsystem is activated by XML descriptors, add `org.wildfly.rule.xml-path-<id>` with the file path and XPath expression (e.g., `/META-INF/persistence.xml,/persistence`).
   - If your subsystem is activated by properties files, add `org.wildfly.rule.properties-file-match-<id>`.
   - If your subsystem is activated by specific files existing, add `org.wildfly.rule.expected-file-<id>`.

3. **Use `*` wildcards** for package-level matching. Glow converts `*` to regex `.*` internally, so `jakarta.persistence.*` matches all classes in that package tree.

4. **If your layer is an optional alternative**, register it as an add-on:
   - On the **parent** layer: add `org.wildfly.rule.expect-add-on-family=<family>`.
   - On each **alternative** layer: add `org.wildfly.rule.add-on=<family>,<name>`, `org.wildfly.rule.add-on-depends-on=<mode>`, and optionally `org.wildfly.rule.add-on-cardinality=<n>`.

5. **If your layer should auto-include when dependencies are present** (without being an add-on), set `org.wildfly.rule.inclusion-mode=all-dependencies` or `org.wildfly.rule.inclusion-mode=only:<layer1>,<layer2>`.

6. **If your layer supports HA profiles**, add `org.wildfly.rule.profile-ha=<ha-variant-layer>` to declare the HA replacement.

7. **Test by running Glow** against a sample deployment that uses your subsystem's API. Glow should discover your layer automatically. Use `--verbose` to see which rules matched.

### Why No Glow Core Changes Are Needed

Glow's rule engine is fully data-driven. `Utils.buildMapping()` reads properties from whatever layers the resolved feature packs provide. `DeploymentScanner` evaluates rules generically — it doesn't know about specific subsystems. The `org.wildfly.rule.*` namespace is a contract between feature pack authors and the Glow engine. As long as your properties use recognized rule names, Glow will process them.

### Example: Minimal JAX-RS Discovery

```xml
<layer-spec xmlns="urn:jboss:galleon:layer-spec:2.0" name="jaxrs">
    <props>
        <prop name="org.wildfly.rule.annotations" value="jakarta.ws.rs,jakarta.ws.rs.core"/>
        <prop name="org.wildfly.rule.class" value="jakarta.ws.rs,jakarta.ws.rs.core"/>
        <prop name="org.wildfly.rule.xml-path"
              value="/WEB-INF/web.xml,/web-app/servlet/servlet-class,jakarta.ws.rs.core.Application"/>
    </props>
    <dependencies>
        <layer name="jaxrs-core"/>
    </dependencies>
</layer-spec>
```

This layer is discovered when a deployment imports any class from `jakarta.ws.rs` or `jakarta.ws.rs.core`, uses annotations from those packages, or declares the JAX-RS `Application` servlet class in `web.xml`.
