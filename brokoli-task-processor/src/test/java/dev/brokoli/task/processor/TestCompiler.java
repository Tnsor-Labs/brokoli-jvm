package dev.brokoli.task.processor;

import dev.brokoli.task.Task;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class TestCompiler {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String BUILD_ENCODING_OTHER_THAN_UTF_8 = "ISO-8859-1";
    private static final List<String> OPTIONS =
            List.of("--release", "17", "-encoding", BUILD_ENCODING_OTHER_THAN_UTF_8, "-Xlint:all");

    private TestCompiler() {
    }

    static Compilation compile(Path output, Source... sources) {
        return compile(output, List.of(), sources);
    }

    static Compilation compile(Path output, List<Processor> otherProcessors, Source... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            Files.createDirectories(output);
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            files.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(taskApi()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, OPTIONS, null,
                    Arrays.stream(sources).map(Source::asFile).toList());
            List<Processor> processors = new ArrayList<>();
            processors.add(new TaskProcessor());
            processors.addAll(otherProcessors);
            task.setProcessors(processors);
            Compilation compilation = new Compilation(task.call(), diagnostics.getDiagnostics(), output);
            requireCoreSchema(output);
            requireCleanTaskClasses(compilation.diagnostics());
            return compilation;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void requireCleanTaskClasses(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<String> generated = diagnostics.stream()
                .filter(diagnostic -> diagnostic.getSource() != null
                        && diagnostic.getSource().getName().endsWith(TaskClassWriter.SUFFIX + ".java"))
                .map(diagnostic -> diagnostic.getKind() + " " + diagnostic.getMessage(Locale.ROOT))
                .toList();
        if (!generated.isEmpty()) {
            throw new AssertionError("the generated task class does not compile cleanly: " + generated);
        }
    }

    private static void requireCoreSchema(Path output) throws IOException {
        Path directory = output.resolve(TaskProcessor.TASK_FILE_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> taskFiles = Files.list(directory)) {
            for (Path taskFile : taskFiles.toList()) {
                for (JsonNode task : JSON.readTree(Files.readString(taskFile)).get("tasks")) {
                    List<String> errors = new ArrayList<>(CoreSchema.parameterErrors(task.get("parameters")));
                    if (!task.get("interface").isNull()) {
                        errors.addAll(CoreSchema.interfaceErrors(task.get("interface")));
                    }
                    if (!errors.isEmpty()) {
                        throw new AssertionError(taskFile.getFileName() + " task " + task.get("method").asString()
                                + " does not match core's task-interface-v1.json: " + errors);
                    }
                }
            }
        }
    }

    private static Path taskApi() {
        try {
            return Path.of(Task.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException notAPath) {
            throw new IllegalStateException(notAPath);
        }
    }

    record Source(String qualifiedName, String code) {

        static Source of(String qualifiedName, String code) {
            return new Source(qualifiedName, code);
        }

        JavaFileObject asFile() {
            URI uri = URI.create("string:///" + qualifiedName.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension);
            return new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return code;
                }
            };
        }
    }

    record Compilation(boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path output) {

        List<String> errors() {
            return diagnostics.stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                    .toList();
        }

        Path taskFilePath(String binaryName) {
            return output.resolve(TaskProcessor.TASK_FILE_DIRECTORY + binaryName + ".json");
        }

        JsonNode taskFile(String binaryName) {
            try {
                return JSON.readTree(Files.readString(taskFilePath(binaryName)));
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }

        JsonNode task(String binaryName, String method) {
            for (JsonNode task : taskFile(binaryName).get("tasks")) {
                if (task.get("method").asString().equals(method)) {
                    return task;
                }
            }
            throw new AssertionError("no task " + method + " in " + binaryName);
        }

        Object call(String entrypoint, Map<String, Object> kwargs) {
            String className = entrypoint.substring(0, entrypoint.indexOf('#'));
            String methodName = entrypoint.substring(entrypoint.indexOf('#') + 1);
            try (URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()},
                    TestCompiler.class.getClassLoader())) {
                Method entry = Class.forName(className, true, loader).getMethod(methodName, Map.class);
                return entry.invoke(null, kwargs);
            } catch (InvocationTargetException failed) {
                if (failed.getCause() instanceof RuntimeException refused) {
                    throw refused;
                }
                throw new AssertionError(failed.getCause());
            } catch (IOException | ReflectiveOperationException unloadable) {
                throw new AssertionError("could not call " + entrypoint, unloadable);
            }
        }
    }
}
