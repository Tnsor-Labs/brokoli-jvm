# Changelog

All notable changes to the Brokoli JVM SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

Every entry names its owner.

## [Unreleased]

### Added

- **Task API** -- @DarzVadckawy. `brokoli-task` has `@Task` to mark a
  task method, `@Default` to make a task parameter optional, and `Row` and
  `Rows` for rows whose schema a task does not declare. `getDouble`
  accepts any number, because the JVM harness decodes a whole number as a
  `Long`, and every typed getter names the column when its value is
  missing, null or of another type. `@Task` and `@Default` take effect
  once the annotation processor lands. The javadoc jar is built, and a
  public type or member without Javadoc fails the build.
- **Architecture decision records** -- @DarzVadckawy. `docs/adr/` holds
  the decisions that only concern the JVM SDK, in core's format, starting
  with JVM-ADR-001 on the task API.
- **Maven build and CI** -- @DarzVadckawy. A Maven reactor with two
  modules, `brokoli-task` (the task API, with no dependencies, enforced by
  the build) and `brokoli-pipeline` (authoring and packaging), built for
  Java 17. The Maven wrapper pins Maven 3.9.11 and checks its sha256. CI
  runs `./mvnw verify` on JDK 17, 21 and 25.
