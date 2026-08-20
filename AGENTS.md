# WildFly Glow — Agent Instructions

WildFly Glow is a provisioning analysis engine that scans application deployments to determine the minimal set of Galleon feature packs and layers required to run on WildFly. It provides a CLI, a core scanning library, Maven plugin integrations (WildFly Maven Plugin and Arquillian), and deployment support for bare-metal, Docker, and OpenShift environments.

## Navigating This Repository

1. Read `llms.txt` in the repository root for a full index of modules, documentation, and key source entry points.
2. The core scanning logic lives in `core/` — start with `GlowSession.java` for the main orchestration, `ScanArguments.java` for configuration, and `ScanResults.java` for output.
3. The CLI is in `cli/` (picocli commands) and `cli-support/` (shared infrastructure).
4. Maven plugin integrations: `arquillian-plugin/` for test provisioning, `doc-plugin/` for documentation generation.
5. OpenShift deployment support is in `openshift-deployment/` with per-service deployers (PostgreSQL, MySQL, MariaDB, AMQ, Keycloak).
6. Documentation is in `docs/guide/` as AsciiDoc files.

## Key Concepts

- **Galleon Layers**: Server capability units annotated with rules that match deployment content (Java types, XML descriptors, properties files). Glow scans deployments and matches content against these rules.
- **Feature Packs**: Galleon packages containing layers. Glow knows which feature packs are compatible with each WildFly version via the [wildfly-galleon-feature-packs](https://github.com/wildfly/wildfly-galleon-feature-packs) registry.
- **Add-ons**: Optional server features (e.g., SSL, OpenAPI, database drivers) suggested based on discovered layers but not automatically included.
- **Execution Context**: `bare-metal` (default) or `cloud` — affects which layers and feature packs are included.
- **Execution Profile**: Non-HA (default) or `ha` — controls clustering layer inclusion/exclusion.

## Building

```
mvn clean install          # JDK 11+
sh ./tests/run-cli-tests.sh  # CLI integration tests
```

## Ecosystem Context & Cross-Repo Routing

WildFly Glow is part of the WildFly ecosystem. When working on a task, determine whether it is local to this repository or requires navigating to another repository.

- **Local Tasks:** For provisioning analysis, deployment scanning, layer rule matching, add-on management, CLI commands, Arquillian plugin behavior, OpenShift deployment automation, and JBang integration, consult the local [WildFly Glow Documentation Index](https://raw.githubusercontent.com/kabir/wildfly-glow/ai-index/llms.txt).

- **Cross-Repository Tasks:** For changes involving upstream or downstream components, consult the [WildFly Central AI Hub](https://raw.githubusercontent.com/kabir/wildfly-ai-context/main/llms.txt) and look up the target project:
    - *Modifying or querying the management model, controller behavior, deployment scanner SPI, CLI infrastructure, or domain mode* → Navigate to **WildFly Core**.
    - *Modifying Jakarta EE subsystem behavior, Elytron security configuration, clustering/HA internals, or Galleon feature pack definitions for the full distribution* → Navigate to **WildFly**.
    - *Modifying the WildFly Maven Plugin's `package`/`provision` goals, dev-mode, or how it consumes `<discover-provisioning-info/>`* → Navigate to **WildFly Maven Plugin**.
    - *Modifying Galleon layer annotations, layer-spec.xml rule definitions, or the feature-pack compatibility registry* → Navigate to **WildFly Galleon Feature Packs** (external).
