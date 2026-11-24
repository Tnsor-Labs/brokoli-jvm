# brokoli-jvm

The Brokoli SDK for the JVM. It lets you write Brokoli tasks in Java (and other JVM languages), package them as `jvm` task bundles, and build pipelines that compile to Brokoli IR.

It is not usable yet. The design is core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md), and the work is tracked in this repo's issues.

## Modules

- `brokoli-task` is the task API: the `@Task` annotation and the row types. It has no dependencies, because it sits on the classpath of every task and anything it pulled in would clash with the task's own dependencies.
- `brokoli-task-processor` is the annotation processor. At compile time it checks every `@Task` method and writes its task interface and parameters to `META-INF/brokoli/tasks/<class>.json`. It runs only in the compiler, so it never reaches a task's classpath.
- `brokoli-bundle` packages a compiled project's tasks into task-bundle/v2 archives, one per task, in the form core's `brokoli bundle jvm` writes.
- `brokoli-maven-plugin` runs it in a Maven build and attaches the bundles to the project.
- `brokoli-pipeline` is the authoring side: the pipeline builder, which compiles a pipeline to Brokoli IR and packages the tasks its `task` nodes run.

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

`mvn package` then writes `target/<finalName>-<method>.tar.gz` for each task and prints its digest, the `sha256:` a pipeline uses to reference it. Each bundle is attached with the method name as its classifier, so `mvn install` and `mvn deploy` publish it next to the jar. A bundle holds the project's classes and resources, and the jar of every `compile` and `runtime` dependency under `lib/`, `brokoli-task` included. The build fails on a bundle core would refuse on upload: more than 10000 entries, more than 64 MiB of files, or an archive over 64 MiB, with a message naming what takes the most. `-Dbrokoli.bundle.skip` skips the goal.

## Building a pipeline

`brokoli-pipeline` has a method on `Pipeline` for each node type, named as in the Python and TypeScript SDKs, each taking a record of the node's options. `then` draws an edge and returns the node it points to:

```java
Pipeline p = Pipeline.builder("Sensor Readings")
        .project(TaskProject.of(Path.of("target/classes"), jars))
        .build();
DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/readings.json").format("json"));
NodeRef scaled = rows.then(p.task(Scores.class, "dailyRollup"));

String ir = p.toJson();
String digest = p.digest();
```

`task` adds a `task` node for a `@Task` method. The first call packages the project into one bundle per task, as the Maven plugin does, and the node references its task's bundle by digest. `p.taskBundles()` returns the bundles to upload before the pipeline. A node's id is made from its name, so `"Read"` is `read_1`. `p.render()` is the canonical rendering of core's `ir-canonicalization.md`, and `p.digest()` is its `sha256:`.

The builder takes the pipeline's schedule, SLA, dependencies, webhook and hooks, and every node takes a node key, retries, a retry delay, a timeout and a pool. Times are `java.time` values, written in the units the server reads: seconds for a timeout, milliseconds for a retry delay.

```java
Pipeline p = Pipeline.builder("Hourly Slices")
        .schedule("0 * * * *")
        .scheduleTimezone(ZoneId.of("Africa/Maputo"))
        .sla(LocalTime.of(7, 30), ZoneId.of("Africa/Maputo"))
        .onFailure("https://hooks.example/oncall")
        .build();
DatasetRef rows = p.sourceFile("Read", SourceFile.options()
        .path("/data/in.csv")
        .retries(3)
        .retryDelay(Duration.ofSeconds(2))
        .timeout(Duration.ofMinutes(1)));
p.bash("Refresh", rows, Bash.options().command("./refresh.sh").nodeKey("refresh"));
```

A database source, a join, a quality check and a database sink:

```java
DatasetRef orders = p.sourceDb("Orders", SourceDb.options().connId("prod_pg").query("SELECT * FROM orders"));
DatasetRef users = p.sourceDb("Users", SourceDb.options().connId("prod_pg").query("SELECT id, email FROM users"));
DatasetRef joined = p.join("Enrich", orders, users, Join.options().on("user_id").type(JoinType.LEFT));
NodeRef gated = joined.then(p.qualityCheck("Gate", QualityCheck.options().rules(List.of(
        Map.of("rule", "not_null", "column", "email", "on_failure", "block")))));
gated.then(p.sinkDb("Load", SinkDb.options().connId("warehouse").table("orders_enriched").upsert("id")));
```

A join type, a collision policy and a write mode are enums, so a misspelled one is a compile error; core runs an unknown join type as an inner join. `rightAlias` sets the alias collision policy and `upsert` the upsert mode, each with the value it needs, so the combinations core refuses cannot be written.

An HTTP source, a script, an HTTP sink and a Slack message:

```java
DatasetRef items = p.sourceApi("Fetch", SourceApi.options()
        .url("https://api.example.com/items")
        .headers(Map.of("X-Api-Key", "${secret.items_key}"))
        .records("data.items"));
NodeRef shaped = p.code("Shape", items, Code.python("output_data = {'columns': columns, 'rows': rows}"));
p.sinkApi("Push", shaped, SinkApi.options().url("/ingest").connId("sink_api").batchSize(250));
p.notify("Alert", shaped, Notify.slack("https://hooks.slack.com/services/T0/B0/x").channel("#data-alerts"));
```

A script's language and a message's type are enums too, and each comes with the options it takes: only `Code.python` takes a Python interpreter, only `Code.typescript` a Node executable, and only `Notify.slack` a channel. A `sourceApi` reads a dataset; the scalar and artifact responses come later (#46).

So far the builder has `sourceFile`, `sourceApi`, `sourceDb`, `transform`, `join`, `qualityCheck`, `code`, `notify`, `sinkFile`, `sinkApi`, `sinkDb`, `bash` and `task`. The other node types come next, one group at a time, each checked against the Python SDK's output in `python-reference/`.

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
