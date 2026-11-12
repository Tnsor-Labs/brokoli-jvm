# brokoli-jvm

The Brokoli SDK for the JVM. It lets you write Brokoli tasks in Java (and other JVM languages), package them as `jvm` task bundles, and build pipelines that compile to Brokoli IR.

It is not usable yet. The design is core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md), and the work is tracked in this repo's issues.

## Modules

- `brokoli-task` is the task API: the `@Task` annotation and the row types. It has no dependencies, because it sits on the classpath of every task and anything it pulled in would clash with the task's own dependencies.
- `brokoli-task-processor` is the annotation processor. At compile time it checks every `@Task` method and writes its task interface and parameters to `META-INF/brokoli/tasks/<class>.json`. It runs only in the compiler, so it never reaches a task's classpath.
- `brokoli-bundle` packages a compiled project's tasks into task-bundle/v2 archives, one per task, in the form core's `brokoli bundle jvm` writes.
- `brokoli-maven-plugin` runs it in a Maven build and attaches the bundles to the project.
- `brokoli-pipeline` is the authoring side: the pipeline builder and IR emission.

## Writing a task

```java
public final class Scores {

    public record Reading(String sensor, double score) {
    }

    @Task(name = "Daily Rollup")
    public static List<Reading> dailyRollup(List<Reading> input, @Default("0.5") double threshold) {
        return input.stream().filter(reading -> reading.score() >= threshold).toList();
    }
}
```

Take and return `Rows` to leave the row schema undeclared, or a `List` of a record to declare it. Every other parameter becomes a pipeline parameter, required unless it has `@Default`, and an annotation named `Nullable` (JSpecify's, for example) makes a value nullable. Since JDK 23, javac runs no annotation processor it finds on the classpath, so name the processor in the build:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>dev.brokoli</groupId>
                <artifactId>brokoli-task-processor</artifactId>
                <version>${brokoli.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

The processor leaves `@Task` and `@Default` unclaimed, so other processors still see them, as Error Prone's [DoNotClaimAnnotations](https://errorprone.info/bugpattern/DoNotClaimAnnotations) rule asks. Under `-Xlint:all`, javac therefore warns `No processor claimed any of these annotations`, as it does for AutoValue. A build that also uses `-Werror` turns that one check off:

```xml
<compilerArgs>
    <arg>-Xlint:all</arg>
    <arg>-Xlint:-processing</arg>
    <arg>-Werror</arg>
</compilerArgs>
```

## Packaging tasks

`brokoli-maven-plugin` writes one task bundle per `@Task` method when the project is packaged:

```xml
<plugin>
    <groupId>dev.brokoli</groupId>
    <artifactId>brokoli-maven-plugin</artifactId>
    <version>${brokoli.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>bundle</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

`mvn package` then writes `target/<finalName>-<method>.tar.gz` for each task and prints its digest, the `sha256:` a pipeline uses to reference it. Each bundle is attached with the method name as its classifier, so `mvn install` and `mvn deploy` publish it next to the jar. A bundle holds the project's classes and resources, and the jar of every `compile` and `runtime` dependency under `lib/`, `brokoli-task` included. `-Dbrokoli.bundle.skip` skips the goal.

## How it fits with the rest of Brokoli

- **Java 17 is the floor**, the same minimum core's JVM harness requires (`pkg/taskharness/jvmharness/invocation.go`). CI builds on 17, 21 and 25.
- **The IR matches the Python SDK byte for byte.** For the same pipeline, the IR and its `sha256:` digest must be identical to what [brokoli-sdk](https://github.com/Tnsor-Labs/brokoli-sdk) emits, following core's `docs/schema/ir-canonicalization.md`. [brokoli-typescript](https://github.com/Tnsor-Labs/brokoli-typescript) enforces the same rule with differential fixtures. Schemas are copied from core's `docs/schema/` and checked against it, never edited here.
- **Tasks run in core's JVM harness.** `Harness.java` calls a `public static` method that takes no arguments or a single `java.util.Map`, with staged input rows under the `"input"` key. The SDK builds on that contract.

## Building

```sh
./mvnw verify
```

On Windows use `mvnw.cmd`. The end-to-end test, which runs a bundle on a core server, needs one running; [CONTRIBUTING.md](CONTRIBUTING.md) says how to run it. Contributions follow CONTRIBUTING.md.

## License

Apache License 2.0, see [LICENSE](LICENSE).
