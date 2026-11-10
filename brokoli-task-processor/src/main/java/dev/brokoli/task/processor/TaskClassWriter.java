package dev.brokoli.task.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

final class TaskClassWriter {

    static final String SUFFIX = "_BrokoliTasks";

    private static final String VALUES_CLASS = "dev.brokoli.task.internal.TaskValues";
    private static final String VALUES = "TaskValues";
    private static final String ENTRY_SEPARATOR = "#";
    private static final String INDENT = "    ";
    private static final String CONTINUATION = INDENT + INDENT + INDENT + INDENT;
    private static final String NULLABLE_KEY = "nullable ";
    private static final char LAST_PRINTABLE_ASCII = '~';
    private static final char FIRST_PRINTABLE_ASCII = ' ';

    private static final Map<String, String> SCALAR_READERS = Map.ofEntries(
            Map.entry("long", "readLong"), Map.entry("java.lang.Long", "readLong"),
            Map.entry("int", "readInt"), Map.entry("java.lang.Integer", "readInt"),
            Map.entry("double", "readDouble"), Map.entry("java.lang.Double", "readDouble"),
            Map.entry("float", "readFloat"), Map.entry("java.lang.Float", "readFloat"),
            Map.entry("boolean", "readBoolean"), Map.entry("java.lang.Boolean", "readBoolean"),
            Map.entry("java.lang.String", "readString"),
            Map.entry("java.math.BigDecimal", "readDecimal"),
            Map.entry("java.time.LocalDate", "readDate"),
            Map.entry("java.time.Instant", "readTimestamp"),
            Map.entry("java.time.Duration", "readDuration"),
            Map.entry("byte[]", "readBytes"),
            Map.entry(TypeMapper.ROWS, "readRows"));

    private static final Map<String, String> SCALAR_WRITERS = Map.ofEntries(
            Map.entry("long", "plain"), Map.entry("java.lang.Long", "plain"),
            Map.entry("int", "writeInt"), Map.entry("java.lang.Integer", "writeInt"),
            Map.entry("double", "plain"), Map.entry("java.lang.Double", "plain"),
            Map.entry("float", "plain"), Map.entry("java.lang.Float", "plain"),
            Map.entry("boolean", "plain"), Map.entry("java.lang.Boolean", "plain"),
            Map.entry("java.lang.String", "plain"),
            Map.entry("java.math.BigDecimal", "writeDecimal"),
            Map.entry("java.time.LocalDate", "writeDate"),
            Map.entry("java.time.Instant", "writeTimestamp"),
            Map.entry("java.time.Duration", "writeDuration"),
            Map.entry("byte[]", "writeBytes"),
            Map.entry(TypeMapper.ROWS, "writeRows"));

    private final TypeElement owner;
    private final String className;
    private final Map<String, String> readers = new LinkedHashMap<>();
    private final Map<String, String> writers = new LinkedHashMap<>();
    private final List<String> constants = new ArrayList<>();
    private final List<String> readHelpers = new ArrayList<>();
    private final List<String> writeHelpers = new ArrayList<>();

    private TaskClassWriter(TypeElement owner) {
        this.owner = owner;
        this.className = className(owner);
    }

    static String qualifiedName(TypeElement owner) {
        PackageElement in = TypeMapper.packageOf(owner);
        return in.isUnnamed() ? className(owner) : in.getQualifiedName() + "." + className(owner);
    }

    static String entrypoint(TypeElement owner, String method) {
        return qualifiedName(owner) + ENTRY_SEPARATOR + method;
    }

    static String source(TypeElement owner, List<ExecutableElement> tasks) {
        TaskClassWriter writer = new TaskClassWriter(owner);
        List<String> entries = new ArrayList<>();
        for (ExecutableElement task : tasks) {
            entries.add(writer.entry(task));
        }
        return asciiOnly(writer.render(entries));
    }

    private static String className(TypeElement owner) {
        List<String> names = new ArrayList<>();
        for (Element enclosing = owner; enclosing instanceof TypeElement type;
                enclosing = enclosing.getEnclosingElement()) {
            names.add(0, type.getSimpleName().toString());
        }
        return String.join("_", names) + SUFFIX;
    }

    private String entry(ExecutableElement task) {
        List<? extends VariableElement> parameters = task.getParameters();
        List<String> arguments = new ArrayList<>();
        arguments.add(VALUES + ".input(kwargs, " + reader(parameters.get(0).asType(), false) + ")");
        for (VariableElement parameter : parameters.subList(1, parameters.size())) {
            boolean nullable = TypeMapper.isNullable(parameter, parameter.asType());
            arguments.add(VALUES + ".parameter(kwargs, " + literal(parameter.getSimpleName().toString()) + ", "
                    + reader(parameter.asType(), nullable) + ")");
        }
        String call = owner.getQualifiedName() + "." + task.getSimpleName() + "(\n" + CONTINUATION
                + String.join(",\n" + CONTINUATION, arguments) + ")";
        return INDENT + "public static Object " + task.getSimpleName() + "(java.util.Map<String, Object> kwargs) {\n"
                + INDENT + INDENT + "return " + VALUES + ".result(" + call + ",\n" + CONTINUATION
                + writer(task.getReturnType()) + ");\n"
                + INDENT + "}\n";
    }

    private String reader(TypeMirror type, boolean nullable) {
        String scalar = SCALAR_READERS.get(typeName(type));
        if (scalar != null && !nullable) {
            return VALUES + "::" + scalar;
        }
        String key = (nullable ? NULLABLE_KEY : "") + typeName(type);
        String existing = readers.get(key);
        if (existing != null) {
            return className + "::" + existing;
        }
        int slot = readHelpers.size();
        readHelpers.add("");
        String name = "read$" + slot;
        readers.put(key, name);
        String body = nullable
                ? "return " + VALUES + ".nullable(value, " + reader(type, false) + ");\n"
                : readBody(type);
        readHelpers.set(slot, helper(typeName(type) + " " + name + "(Object value)", body));
        return className + "::" + name;
    }

    private String readBody(TypeMirror type) {
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
        if (element.getKind() == ElementKind.ENUM) {
            String field = "ENUM$" + constants.size();
            constants.add(INDENT + "private static final " + element.getQualifiedName() + "[] " + field + " = "
                    + element.getQualifiedName() + ".values();\n");
            return "return " + VALUES + ".readEnum(value, " + field + ");\n";
        }
        if (element.getKind() == ElementKind.RECORD) {
            return readRecordBody(element);
        }
        TypeMirror item = arguments.get(arguments.size() - 1);
        String method = arguments.size() == 1 ? ".readList(value, " : ".readMap(value, ";
        return "return " + VALUES + method + reader(item, TypeMapper.isNullable(item)) + ");\n";
    }

    private String readRecordBody(TypeElement record) {
        List<String> fields = new ArrayList<>();
        for (RecordComponentElement component : record.getRecordComponents()) {
            fields.add(VALUES + ".field(fields, " + literal(component.getSimpleName().toString()) + ", "
                    + reader(component.asType(), TypeMapper.isNullable(record, component)) + ")");
        }
        return "java.util.Map<?, ?> fields = " + VALUES + ".readRecord(value);\n"
                + "return new " + record.getQualifiedName() + "(\n" + INDENT + INDENT
                + String.join(",\n" + INDENT + INDENT, fields) + ");\n";
    }

    private String writer(TypeMirror type) {
        String scalar = SCALAR_WRITERS.get(typeName(type));
        if (scalar != null) {
            return VALUES + "::" + scalar;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        if (element.getKind() == ElementKind.ENUM) {
            return VALUES + "::writeEnum";
        }
        String key = typeName(type);
        String existing = writers.get(key);
        if (existing != null) {
            return className + "::" + existing;
        }
        int slot = writeHelpers.size();
        writeHelpers.add("");
        String name = "write$" + slot;
        writers.put(key, name);
        String body;
        String returned;
        if (element.getKind() == ElementKind.RECORD) {
            body = writeRecordBody(element);
            returned = "java.util.Map<String, Object>";
        } else {
            List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
            boolean list = arguments.size() == 1;
            body = "return " + VALUES + (list ? ".writeList(value, " : ".writeMap(value, ")
                    + writer(arguments.get(arguments.size() - 1)) + ");\n";
            returned = list ? "java.util.List<Object>" : "java.util.Map<String, Object>";
        }
        writeHelpers.set(slot, helper(returned + " " + name + "(" + typeName(type) + " value)", body));
        return className + "::" + name;
    }

    private static String helper(String signature, String body) {
        return INDENT + "private static " + signature + " {\n" + indented(body) + INDENT + "}\n";
    }

    private String writeRecordBody(TypeElement record) {
        StringBuilder body = new StringBuilder();
        body.append("if (value == null) {\n").append(INDENT).append("return null;\n}\n");
        body.append("java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();\n");
        for (RecordComponentElement component : record.getRecordComponents()) {
            String name = component.getSimpleName().toString();
            body.append(VALUES).append(".put(fields, ").append(literal(name)).append(", value.").append(name)
                    .append("(), ").append(writer(component.asType())).append(");\n");
        }
        return body.append("return fields;\n").toString();
    }

    private String render(List<String> entries) {
        PackageElement in = TypeMapper.packageOf(owner);
        StringBuilder source = new StringBuilder();
        if (!in.isUnnamed()) {
            source.append("package ").append(in.getQualifiedName()).append(";\n\n");
        }
        source.append("import ").append(VALUES_CLASS).append(";\n\n");
        source.append("public final class ").append(className).append(" {\n\n");
        for (String constant : constants) {
            source.append(constant);
        }
        if (!constants.isEmpty()) {
            source.append('\n');
        }
        source.append(INDENT).append("private ").append(className).append("() {\n").append(INDENT).append("}\n");
        for (String entry : entries) {
            source.append('\n').append(entry);
        }
        for (String helper : readHelpers) {
            source.append('\n').append(helper);
        }
        for (String helper : writeHelpers) {
            source.append('\n').append(helper);
        }
        return source.append("}\n").toString();
    }

    private static String typeName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return type.getKind().name().toLowerCase(Locale.ROOT);
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return typeName(((ArrayType) type).getComponentType()) + "[]";
        }
        DeclaredType declared = (DeclaredType) type;
        String name = ((TypeElement) declared.asElement()).getQualifiedName().toString();
        if (declared.getTypeArguments().isEmpty()) {
            return name;
        }
        StringJoiner arguments = new StringJoiner(", ", name + "<", ">");
        declared.getTypeArguments().forEach(argument -> arguments.add(typeName(argument)));
        return arguments.toString();
    }

    private static String indented(String body) {
        StringBuilder indented = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (!line.isEmpty()) {
                indented.append(INDENT).append(INDENT).append(line).append('\n');
            }
        }
        return indented.toString();
    }

    private static String literal(String text) {
        return "\"" + text + "\"";
    }

    private static String asciiOnly(String source) {
        StringBuilder ascii = new StringBuilder(source.length());
        for (char unit : source.toCharArray()) {
            boolean printable = unit >= FIRST_PRINTABLE_ASCII && unit <= LAST_PRINTABLE_ASCII;
            if (printable || unit == '\n') {
                ascii.append(unit);
            } else {
                ascii.append(String.format("\\u%04x", (int) unit));
            }
        }
        return ascii.toString();
    }

}
