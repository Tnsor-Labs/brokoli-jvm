package dev.brokoli.task.processor;

import dev.brokoli.task.Task;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads every {@link Task} method at compile time and writes its task interface and pipeline parameters to
 * {@code META-INF/brokoli/tasks/<class>.json} in the class output, one file per class that declares tasks.
 *
 * <p>A method that cannot be a task is refused with a compile error on that method or parameter: one that is not
 * {@code public static}, sits in a class the harness cannot reach, takes or returns something other than
 * {@code Rows} or a {@code List} of a record, or has a parameter or {@code @Default} the task interface cannot
 * describe. Since JDK 23, javac runs no processor it finds on the classpath, so the build has to name this one, for
 * example in {@code annotationProcessorPaths} of {@code maven-compiler-plugin}.
 */
@SupportedAnnotationTypes(TaskProcessor.TASK_ANNOTATION)
public final class TaskProcessor extends AbstractProcessor {

    static final String TASK_ANNOTATION = "dev.brokoli.task.Task";
    static final String TASK_FILE_DIRECTORY = "META-INF/brokoli/tasks/";
    static final String TASK_FILE_FORMAT = "brokoli.jvm-tasks/v1";

    private static final String FORMAT = "format";
    private static final String CLASS = "class";
    private static final String TASKS = "tasks";
    private static final String METHOD = "method";
    private static final String NAME = "name";
    private static final String INTERFACE = "interface";
    private static final String PARAMETERS = "parameters";
    private static final String JSON_EXTENSION = ".json";

    private final TaskReader reader = new TaskReader();

    /**
     * Creates the processor. javac creates it through {@code META-INF/services}.
     */
    public TaskProcessor() {
    }

    /**
     * The latest source version this JDK supports, so the processor runs on every JDK from 17 on without a
     * warning.
     *
     * @return the latest supported source version
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Writes the task file for every class in this round that declares {@link Task} methods, and reports a
     * compile error for each method that cannot be a task. A class with an invalid task gets no file.
     *
     * @param annotations the annotation types requested to be processed
     * @param round the current round
     * @return {@code false}, so other processors can also see {@link Task}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (TypeElement owner : owners(round)) {
            writeTaskFile(owner);
        }
        return false;
    }

    private static Set<TypeElement> owners(RoundEnvironment round) {
        Set<TypeElement> owners = new TreeSet<>(Comparator.comparing(owner -> owner.getQualifiedName().toString()));
        for (Element element : round.getElementsAnnotatedWith(Task.class)) {
            owners.add((TypeElement) element.getEnclosingElement());
        }
        return owners;
    }

    private void writeTaskFile(TypeElement owner) {
        List<TaskDeclaration> tasks = new ArrayList<>();
        Set<String> methods = new HashSet<>();
        boolean valid = true;
        for (Element member : owner.getEnclosedElements()) {
            if (!(member instanceof ExecutableElement method) || method.getAnnotation(Task.class) == null) {
                continue;
            }
            try {
                checkUnique(method, owner, methods);
                tasks.add(reader.read(method));
            } catch (InvalidTaskException invalid) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, invalid.getMessage(),
                        invalid.getElement());
                valid = false;
            }
        }
        if (valid) {
            write(owner, tasks);
        }
    }

    private static void checkUnique(ExecutableElement method, TypeElement owner, Set<String> methods) {
        String name = method.getSimpleName().toString();
        if (!methods.add(name)) {
            throw new InvalidTaskException("@Task method " + owner.getSimpleName() + "." + name
                    + " is declared twice; the harness finds a task by its method name, so each must be unique",
                    method);
        }
    }

    private void write(TypeElement owner, List<TaskDeclaration> tasks) {
        String binaryName = processingEnv.getElementUtils().getBinaryName(owner).toString();
        String path = TASK_FILE_DIRECTORY + binaryName + JSON_EXTENSION;
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path, owner);
            try (Writer out = file.openWriter()) {
                out.write(JsonWriter.write(document(binaryName, tasks)));
                out.write('\n');
            }
        } catch (IOException unwritable) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "could not write " + path + ": " + unwritable.getMessage(), owner);
        }
    }

    private static Map<String, Object> document(String binaryName, List<TaskDeclaration> tasks) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TaskDeclaration task : tasks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(METHOD, task.method());
            entry.put(NAME, task.name());
            entry.put(INTERFACE, task.nodeInterface());
            entry.put(PARAMETERS, task.parameters());
            entries.add(entry);
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(FORMAT, TASK_FILE_FORMAT);
        document.put(CLASS, binaryName);
        document.put(TASKS, entries);
        return document;
    }
}
