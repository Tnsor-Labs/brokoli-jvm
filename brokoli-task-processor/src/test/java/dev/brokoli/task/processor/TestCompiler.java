package dev.brokoli.task.processor;

import dev.brokoli.task.Task;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class TestCompiler {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private static final List<String> OPTIONS = List.of("--release", "17");

    private TestCompiler() {
    }

    static Compilation compile(Path output, Source... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            files.setLocationFromPaths(StandardLocation.CLASS_PATH, List.of(taskApi()));
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, OPTIONS, null,
                    Arrays.stream(sources).map(Source::asFile).toList());
            task.setProcessors(List.of(new TaskProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics(), output);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
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
    }
}
