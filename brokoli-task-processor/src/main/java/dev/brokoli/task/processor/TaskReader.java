package dev.brokoli.task.processor;

import dev.brokoli.task.Default;
import dev.brokoli.task.Task;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

final class TaskReader {

    private final TypeMapper types = new TypeMapper();

    TaskDeclaration read(ExecutableElement method) {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        String label = "@Task method " + owner.getSimpleName() + "." + method.getSimpleName();
        checkCallable(method, owner, label);
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.isEmpty()) {
            throw new InvalidTaskException(label
                    + " must take its input rows as its first parameter: Rows, or a List of a record", method);
        }
        VariableElement input = parameters.get(0);
        if (input.getAnnotation(Default.class) != null) {
            throw new InvalidTaskException(label + " has @Default on its input rows, which always come from the "
                    + "upstream node", input);
        }
        Map<String, Object> inputRow = types.datasetRow(input.asType(), label + " input", input);
        Map<String, Object> resultRow = types.datasetRow(method.getReturnType(), label + " result", method);
        return new TaskDeclaration(method.getSimpleName().toString(), displayName(method, label),
                nodeInterface(inputRow, resultRow), parameters(parameters.subList(1, parameters.size()), label));
    }

    private static void checkCallable(ExecutableElement method, TypeElement owner, String label) {
        if (!method.getModifiers().contains(Modifier.PUBLIC) || !method.getModifiers().contains(Modifier.STATIC)) {
            throw new InvalidTaskException(label + " must be public static", method);
        }
        if (!method.getTypeParameters().isEmpty()) {
            throw new InvalidTaskException(label + " must not declare type parameters", method);
        }
        for (Element enclosing = owner; enclosing instanceof TypeElement type;
                enclosing = enclosing.getEnclosingElement()) {
            if (!isReachable(type)) {
                throw new InvalidTaskException(label + " is in " + type.getQualifiedName()
                        + ", which must be a public top-level class or a public static nested class", method);
            }
        }
    }

    private static boolean isReachable(TypeElement type) {
        boolean isClass = type.getKind().isClass();
        boolean isPublic = type.getModifiers().contains(Modifier.PUBLIC);
        boolean topLevel = type.getNestingKind() == NestingKind.TOP_LEVEL;
        boolean staticMember = type.getNestingKind() == NestingKind.MEMBER
                && (type.getModifiers().contains(Modifier.STATIC) || type.getKind() != ElementKind.CLASS);
        return isClass && isPublic && (topLevel || staticMember);
    }

    private static String displayName(ExecutableElement method, String label) {
        String explicit = method.getAnnotation(Task.class).name();
        if (explicit.isEmpty()) {
            return TaskNames.displayName(method.getSimpleName().toString());
        }
        if (explicit.isBlank()) {
            throw new InvalidTaskException(label + " has a blank @Task name; leave it out to use the method name",
                    method);
        }
        OptionalInt surrogate = UnicodeText.unpairedSurrogate(explicit);
        if (surrogate.isPresent()) {
            throw new InvalidTaskException(label + " has a @Task name with an unpaired surrogate at index "
                    + surrogate.getAsInt() + "; Brokoli text must be valid Unicode", method);
        }
        return explicit;
    }

    private static Map<String, Object> nodeInterface(Map<String, Object> inputRow, Map<String, Object> resultRow) {
        if (inputRow == null && resultRow == null) {
            return null;
        }
        Map<String, Object> taskInterface = new LinkedHashMap<>();
        taskInterface.put(Bptd.CONTRACT, Bptd.TASK_INTERFACE);
        taskInterface.put(Bptd.INPUTS, Map.of(Bptd.INPUT_PORT, port(inputRow)));
        taskInterface.put(Bptd.OUTPUTS, Map.of(Bptd.RESULT_PORT, port(resultRow)));
        return taskInterface;
    }

    private static Map<String, Object> port(Map<String, Object> row) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put(Bptd.KIND, Bptd.DATASET);
        dataset.put(Bptd.ROW, row == null ? Map.of(Bptd.KIND, Bptd.UNKNOWN) : row);
        return Map.of(Bptd.VALUE, dataset);
    }

    private Map<String, Object> parameters(List<? extends VariableElement> parameters, String label) {
        Map<String, Object> declarations = new LinkedHashMap<>();
        for (VariableElement parameter : parameters) {
            String name = parameter.getSimpleName().toString();
            String context = label + " parameter " + name;
            if (name.equals(Bptd.INPUT_PORT)) {
                throw new InvalidTaskException(context + " is named like the input rows, which the harness passes "
                        + "under 'input'; rename it", parameter);
            }
            if (!name.matches(Bptd.IDENTIFIER)) {
                throw new InvalidTaskException(context + " has a name that is not a Brokoli identifier ("
                        + Bptd.IDENTIFIER + ")", parameter);
            }
            boolean nullable = TypeMapper.isNullable(parameter, parameter.asType());
            Map<String, Object> type = types.map(parameter.asType(), nullable, context, parameter);
            declarations.put(name, declaration(parameter, type, context));
        }
        return declarations;
    }

    private static Map<String, Object> declaration(VariableElement parameter, Map<String, Object> type,
            String context) {
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put(Bptd.TYPE, type);
        Default fallback = parameter.getAnnotation(Default.class);
        if (fallback == null) {
            declaration.put(Bptd.REQUIRED, true);
        } else {
            declaration.put(Bptd.REQUIRED, false);
            declaration.put(Bptd.DEFAULT, DefaultReader.read(fallback.value(), type, context, parameter));
        }
        return declaration;
    }
}
