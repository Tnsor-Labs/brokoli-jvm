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
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
 * For the same class it generates {@code <Class>_BrokoliTasks} in the same package, with one
 * {@code public static Object <method>(Map<String, Object>)} per task: the entry method core's harness calls,
 * which reads the task's input rows and parameters into the method's types and writes back what it returns. Each
 * task's {@code entrypoint} in the task file names it.
 *
 * <p>A method that cannot be a task is refused with a compile error on that method or parameter: one that is not
 * {@code public static}, sits in a class the harness cannot reach, takes or returns something other than
 * {@code Rows} or a {@code List} of a record, has a parameter or {@code @Default} the task interface cannot
 * describe, or uses a record or enum that code in its own package cannot reach.
 *
 * <p>The task file is written in UTF-8 whatever the build's source encoding. Since JDK 23, javac runs no processor
 * it finds on the classpath, so the build has to name this one, for example in {@code annotationProcessorPaths} of
 * {@code maven-compiler-plugin}.
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
    private static final String ENTRYPOINT = "entrypoint";
    private static final String NAME = "name";
    private static final String INTERFACE = "interface";
    private static final String PARAMETERS = "parameters";
    private static final String JSON_EXTENSION = ".json";

    private final TaskReader reader = new TaskReader();
    private final Set<String> deferred = new TreeSet<>();

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
     * <p>A class whose tasks mention a type that does not resolve yet is read again in the next round, because
     * another processor may generate that type. If it still does not resolve when processing ends, javac reports
     * the missing type and the class gets no file.
     *
     * @param annotations the annotation types requested to be processed
     * @param round the current round
     * @return {@code false}, so other processors can also see {@link Task}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) {
            deferred.clear();
            return false;
        }
        Set<TypeElement> owners = owners(round);
        deferred.clear();
        for (TypeElement owner : owners) {
            writeTaskFile(owner);
        }
        return false;
    }

    private Set<TypeElement> owners(RoundEnvironment round) {
        Set<TypeElement> owners = new TreeSet<>(Comparator.comparing(owner -> owner.getQualifiedName().toString()));
        for (Element element : round.getElementsAnnotatedWith(Task.class)) {
            owners.add((TypeElement) element.getEnclosingElement());
        }
        for (String name : deferred) {
            owners.add(processingEnv.getElementUtils().getTypeElement(name));
        }
        return owners;
    }

    private void writeTaskFile(TypeElement owner) {
        List<TaskDeclaration> tasks = new ArrayList<>();
        List<InvalidTaskException> refusals = new ArrayList<>();
        Set<String> methods = new HashSet<>();
        List<ExecutableElement> taskMethods = taskMethods(owner);
        try {
            for (ExecutableElement method : taskMethods) {
                try {
                    checkUnique(method, owner, methods);
                    tasks.add(reader.read(method));
                } catch (InvalidTaskException refused) {
                    refusals.add(refused);
                }
            }
        } catch (UnresolvedTypeException notGeneratedYet) {
            deferred.add(owner.getQualifiedName().toString());
            return;
        }
        if (refusals.isEmpty()) {
            write(owner, tasks);
            writeTaskClass(owner, taskMethods);
        }
        for (InvalidTaskException refused : refusals) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, refused.getMessage(),
                    refused.getElement());
        }
    }

    private static List<ExecutableElement> taskMethods(TypeElement owner) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element member : owner.getEnclosedElements()) {
            if (member instanceof ExecutableElement method && method.getAnnotation(Task.class) != null) {
                methods.add(method);
            }
        }
        return methods;
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
            try (Writer out = new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8.newEncoder())) {
                out.write(JsonWriter.write(document(owner, binaryName, tasks)));
                out.write('\n');
            }
        } catch (IOException unwritable) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "could not write " + path + ": " + unwritable.getMessage(), owner);
        }
    }

    private void writeTaskClass(TypeElement owner, List<ExecutableElement> tasks) {
        String name = TaskClassWriter.qualifiedName(owner);
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(name, owner);
            try (Writer out = file.openWriter()) {
                out.write(TaskClassWriter.source(owner, tasks));
            }
        } catch (IOException unwritable) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "could not write " + name + ": " + unwritable.getMessage(), owner);
        }
    }

    private static Map<String, Object> document(TypeElement owner, String binaryName, List<TaskDeclaration> tasks) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TaskDeclaration task : tasks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(METHOD, task.method());
            entry.put(ENTRYPOINT, TaskClassWriter.entrypoint(owner, task.method()));
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
