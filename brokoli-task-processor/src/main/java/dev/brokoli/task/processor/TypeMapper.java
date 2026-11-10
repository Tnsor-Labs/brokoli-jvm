package dev.brokoli.task.processor;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TypeMapper {

    static final String ROWS = "dev.brokoli.task.Rows";

    private static final String NULLABLE_ANNOTATION = "Nullable";
    private static final String LIST = "java.util.List";
    private static final String MAP = "java.util.Map";
    private static final String STRING = "java.lang.String";
    private static final String SUPPORTED =
            "long, int, double, float, boolean (or their boxed types), String, BigDecimal, LocalDate, Instant, "
                    + "Duration, byte[], an enum, a record, List<T> or Map<String, T>";

    private static final Map<String, String> DECLARED_KINDS = Map.ofEntries(
            Map.entry("java.lang.Long", Bptd.INT64),
            Map.entry("java.lang.Integer", Bptd.INT64),
            Map.entry("java.lang.Double", Bptd.FLOAT64),
            Map.entry("java.lang.Float", Bptd.FLOAT64),
            Map.entry("java.lang.Boolean", Bptd.BOOLEAN),
            Map.entry(STRING, Bptd.STRING),
            Map.entry("java.math.BigDecimal", Bptd.DECIMAL),
            Map.entry("java.time.LocalDate", Bptd.DATE),
            Map.entry("java.time.Instant", Bptd.TIMESTAMP),
            Map.entry("java.time.Duration", Bptd.DURATION));

    Map<String, Object> map(TypeMirror type, boolean nullable, String context, Element at) {
        return map(type, nullable, context, at, new LinkedHashSet<>());
    }

    Map<String, Object> datasetRow(TypeMirror type, String context, Element at) {
        if (isRows(type)) {
            return null;
        }
        TypeElement record = listedRecord(type);
        if (record == null) {
            throw new InvalidTaskException(context + " must be Rows or a List of a record, found " + type, at);
        }
        return describeRecord(record, context, at, new LinkedHashSet<>());
    }

    static boolean isNullable(AnnotatedConstruct... constructs) {
        for (AnnotatedConstruct construct : constructs) {
            if (construct != null && construct.getAnnotationMirrors().stream()
                    .anyMatch(mirror -> mirror.getAnnotationType().asElement().getSimpleName()
                            .contentEquals(NULLABLE_ANNOTATION))) {
                return true;
            }
        }
        return false;
    }

    static boolean isRows(TypeMirror type) {
        return isDeclared(type, ROWS);
    }

    static boolean isDeclared(TypeMirror type, String qualifiedName) {
        return type.getKind() == TypeKind.DECLARED && qualifiedName((DeclaredType) type).equals(qualifiedName);
    }

    private Map<String, Object> map(TypeMirror type, boolean nullable, String context, Element at,
            Set<String> enclosingRecords) {
        Map<String, Object> mapped = describe(type, context, at, enclosingRecords);
        if (nullable) {
            if (type.getKind().isPrimitive()) {
                throw new InvalidTaskException(context + " is a " + type
                        + ", which cannot be null; use its boxed type to make it nullable", at);
            }
            mapped.put(Bptd.NULLABLE, true);
        }
        return mapped;
    }

    private Map<String, Object> describe(TypeMirror type, String context, Element at, Set<String> enclosingRecords) {
        return switch (type.getKind()) {
            case LONG, INT -> kind(Bptd.INT64);
            case DOUBLE, FLOAT -> kind(Bptd.FLOAT64);
            case BOOLEAN -> kind(Bptd.BOOLEAN);
            case ARRAY -> describeArray((ArrayType) type, context, at);
            case DECLARED -> describeDeclared((DeclaredType) type, context, at, enclosingRecords);
            default -> throw unsupported(type, context, at);
        };
    }

    private Map<String, Object> describeArray(ArrayType type, String context, Element at) {
        if (type.getComponentType().getKind() == TypeKind.BYTE) {
            return kind(Bptd.BYTES);
        }
        throw unsupported(type, context, at);
    }

    private Map<String, Object> describeDeclared(DeclaredType type, String context, Element at,
            Set<String> enclosingRecords) {
        String name = qualifiedName(type);
        String kind = DECLARED_KINDS.get(name);
        if (kind != null) {
            return kind(kind);
        }
        if (name.equals(LIST)) {
            return describeList(type, context, at, enclosingRecords);
        }
        if (name.equals(MAP)) {
            return describeMap(type, context, at, enclosingRecords);
        }
        TypeElement element = (TypeElement) type.asElement();
        if (element.getKind() == ElementKind.ENUM) {
            return describeEnum(element);
        }
        if (element.getKind() == ElementKind.RECORD) {
            return describeRecord(element, context, at, enclosingRecords);
        }
        throw unsupported(type, context, at);
    }

    private Map<String, Object> describeList(DeclaredType type, String context, Element at,
            Set<String> enclosingRecords) {
        TypeMirror item = onlyTypeArgument(type, 0, 1, context, at);
        Map<String, Object> array = kind(Bptd.ARRAY);
        array.put(Bptd.ITEMS, map(item, isNullable(item), context + "[]", at, enclosingRecords));
        return array;
    }

    private Map<String, Object> describeMap(DeclaredType type, String context, Element at,
            Set<String> enclosingRecords) {
        TypeMirror key = onlyTypeArgument(type, 0, 2, context, at);
        TypeMirror value = onlyTypeArgument(type, 1, 2, context, at);
        if (key.getKind() != TypeKind.DECLARED || !qualifiedName((DeclaredType) key).equals(STRING)) {
            throw new InvalidTaskException(context + " is a Map with " + key
                    + " keys; a Brokoli map has String keys", at);
        }
        Map<String, Object> map = kind(Bptd.MAP);
        map.put(Bptd.KEYS, Bptd.STRING);
        map.put(Bptd.VALUES, map(value, isNullable(value), context + "{}", at, enclosingRecords));
        return map;
    }

    private static Map<String, Object> describeEnum(TypeElement element) {
        List<String> constants = new ArrayList<>();
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.ENUM_CONSTANT) {
                constants.add(enclosed.getSimpleName().toString());
            }
        }
        Map<String, Object> enumeration = kind(Bptd.ENUM);
        enumeration.put(Bptd.VALUES, constants);
        return enumeration;
    }

    private Map<String, Object> describeRecord(TypeElement record, String context, Element at,
            Set<String> enclosingRecords) {
        String recordName = record.getQualifiedName().toString();
        if (enclosingRecords.contains(recordName)) {
            throw new InvalidTaskException(context + " makes record " + record.getSimpleName()
                    + " contain itself; a row schema cannot be recursive", at);
        }
        Set<String> nested = new LinkedHashSet<>(enclosingRecords);
        nested.add(recordName);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (RecordComponentElement component : record.getRecordComponents()) {
            String fieldName = component.getSimpleName().toString();
            String fieldContext = context + "." + fieldName;
            if (!fieldName.matches(Bptd.IDENTIFIER)) {
                throw new InvalidTaskException(fieldContext + " has a name that is not a Brokoli identifier ("
                        + Bptd.IDENTIFIER + ")", at);
            }
            boolean nullable = isNullable(component, component.getAccessor(), fieldOf(record, fieldName),
                    component.asType());
            Map<String, Object> field = new LinkedHashMap<>();
            field.put(Bptd.NAME, fieldName);
            field.put(Bptd.TYPE, map(component.asType(), nullable, fieldContext, at, nested));
            field.put(Bptd.REQUIRED, true);
            fields.add(field);
        }
        Map<String, Object> described = kind(Bptd.RECORD);
        described.put(Bptd.FIELDS, fields);
        described.put(Bptd.ADDITIONAL_FIELDS, false);
        return described;
    }

    private static TypeElement listedRecord(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED || !qualifiedName((DeclaredType) type).equals(LIST)) {
            return null;
        }
        List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
        if (arguments.size() != 1 || arguments.get(0).getKind() != TypeKind.DECLARED) {
            return null;
        }
        TypeElement element = (TypeElement) ((DeclaredType) arguments.get(0)).asElement();
        return element.getKind() == ElementKind.RECORD ? element : null;
    }

    private static Element fieldOf(TypeElement record, String name) {
        for (Element enclosed : record.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD && enclosed.getSimpleName().contentEquals(name)) {
                return enclosed;
            }
        }
        return null;
    }

    private static TypeMirror onlyTypeArgument(DeclaredType type, int index, int expected, String context,
            Element at) {
        List<? extends TypeMirror> arguments = type.getTypeArguments();
        if (arguments.size() != expected) {
            throw new InvalidTaskException(context + " is a raw " + qualifiedName(type)
                    + "; give it its type arguments", at);
        }
        TypeMirror argument = arguments.get(index);
        if (argument.getKind() == TypeKind.WILDCARD || argument.getKind() == TypeKind.TYPEVAR) {
            throw new InvalidTaskException(context + " is a " + type
                    + "; a Brokoli type needs concrete type arguments, not wildcards or type variables", at);
        }
        return argument;
    }

    private static String qualifiedName(DeclaredType type) {
        return ((TypeElement) type.asElement()).getQualifiedName().toString();
    }

    private static Map<String, Object> kind(String kind) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put(Bptd.KIND, kind);
        return type;
    }

    private static InvalidTaskException unsupported(TypeMirror type, String context, Element at) {
        return new InvalidTaskException(context + " is a " + type + ", which has no Brokoli type; use "
                + SUPPORTED, at);
    }
}
