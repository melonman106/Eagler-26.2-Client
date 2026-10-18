package net.lax1dude.eaglercraft.v1_8.teavmcompat.dfu;

import java.lang.reflect.Array;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Proof-local materializer for the closed DFU TypeToken witness table.
 *
 * <p>This class is intentionally not a general reflection replacement.  The
 * table contains only the direct anonymous TypeToken classes selected by the
 * proof builder.  In particular, an unknown Class keeps TeaVM's production
 * behavior: its generic superclass is the raw {@link Class#getSuperclass()}
 * value, rather than a strict metadata error.  That sparse fallback is the
 * control which prevents this experiment from becoming a global ABI change.
 */
public final class EaglerDfuGenericMetadata {
    public static final byte CLASS = 1;
    public static final byte PRIMITIVE = 2;
    public static final byte VOID = 3;
    public static final byte ARRAY = 4;
    public static final byte PARAMETERIZED = 5;
    public static final byte WILDCARD = 6;
    public static final byte VARIABLE = 7;

    public static final byte CLASS_DECLARATION = 1;
    public static final byte METHOD_DECLARATION = 2;
    public static final byte CONSTRUCTOR_DECLARATION = 3;

    private static Table installed;
    private static ClassResolver resolver;
    private static Type[] types;
    private static GenericDeclaration[] declarations;
    private static Map<String, ClassRecord> classes;
    private static Map<MemberKey, Integer> members;

    private EaglerDfuGenericMetadata() {
    }

    public interface ClassResolver {
        Class<?> resolve(String internalName);
    }

    public static final class Table {
        public final Node[] types;
        public final Declaration[] declarations;
        public final ClassRecord[] classes;

        public Table(Node[] types, Declaration[] declarations, ClassRecord[] classes) {
            this.types = copy(types);
            this.declarations = copy(declarations);
            this.classes = copy(classes);
        }
    }

    public static final class Node {
        public final byte kind;
        public final String name;
        public final char descriptor;
        public final int declaration;
        public final int component;
        public final int raw;
        public final int owner;
        public final int[] arguments;
        public final int[] upper;
        public final int[] lower;
        public final int[] bounds;

        public Node(byte kind, String name, char descriptor, int declaration, int component, int raw, int owner,
                int[] arguments, int[] upper, int[] lower, int[] bounds) {
            this.kind = kind;
            this.name = name;
            this.descriptor = descriptor;
            this.declaration = declaration;
            this.component = component;
            this.raw = raw;
            this.owner = owner;
            this.arguments = copy(arguments);
            this.upper = copy(upper);
            this.lower = copy(lower);
            this.bounds = copy(bounds);
        }
    }

    public static final class Declaration {
        public final byte kind;
        public final String owner;
        public final String name;
        public final String descriptor;
        public final int[] parameters;

        public Declaration(byte kind, String owner, String name, String descriptor, int[] parameters) {
            this.kind = kind;
            this.owner = Objects.requireNonNull(owner);
            this.name = name;
            this.descriptor = descriptor;
            this.parameters = copy(parameters);
        }
    }

    public static final class ClassRecord {
        public final String name;
        public final int declaration;
        public final boolean anonymous;
        public final int superclass;
        public final int[] interfaces;

        public ClassRecord(String name, int declaration, boolean anonymous, int superclass, int[] interfaces) {
            this.name = Objects.requireNonNull(name);
            this.declaration = declaration;
            this.anonymous = anonymous;
            this.superclass = superclass;
            this.interfaces = copy(interfaces);
        }
    }

    public static synchronized void install(Table table, ClassResolver classResolver) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(classResolver, "resolver");
        if (installed != null) {
            if (installed == table && resolver == classResolver) return;
            throw new IllegalStateException("DFU proof metadata already installed");
        }
        validate(table);
        installed = table;
        resolver = classResolver;
        types = new Type[table.types.length];
        declarations = new GenericDeclaration[table.declarations.length];
        classes = new HashMap<>();
        members = new HashMap<>();
        for (ClassRecord record : table.classes) {
            if (classes.put(record.name, record) != null) {
                throw new IllegalArgumentException("duplicate proof class metadata: " + record.name);
            }
        }
        for (int i = 0; i < table.declarations.length; ++i) {
            Declaration declaration = table.declarations[i];
            if (declaration.kind != CLASS_DECLARATION) {
                MemberKey key = new MemberKey(declaration.kind, declaration.owner, declaration.name,
                        declaration.descriptor);
                if (members.put(key, i) != null) {
                    throw new IllegalArgumentException("duplicate proof declaration: " + key);
                }
            }
        }
    }

    /** Called by the proof-only TeaVM Class donor. */
    public static Type getGenericSuperclass(Class<?> owner) {
        if (!owner.getName().startsWith("com.mojang.datafixers.")) return owner.getSuperclass();
        EaglerDfuMetadataInstaller.install();
        ClassRecord record = classes.get(internalName(owner));
        // Sparse by design: preserve today's production raw-superclass result.
        if (record == null) return owner.getSuperclass();
        return record.superclass < 0 ? null : type(record.superclass);
    }

    public static TypeVariable<?>[] getTypeParameters(Method method) {
        int declaration = memberDeclaration(method.getDeclaringClass(), METHOD_DECLARATION, method.getName(),
                methodDescriptor(method));
        return declarationParameters(declaration);
    }

    public static Type typeForTesting(int id) {
        return type(id);
    }

    private static int memberDeclaration(Class<?> owner, byte kind, String name, String descriptor) {
        requireInstalled();
        Integer id = members.get(new MemberKey(kind, internalName(owner), name, descriptor));
        if (id == null) {
            throw new IllegalStateException("DFU proof declaration unavailable: " + owner.getName() + "." + name
                    + descriptor);
        }
        return id.intValue();
    }

    private static TypeVariable<?>[] declarationParameters(int declarationId) {
        Declaration declaration = declarationRecord(declarationId);
        TypeVariable<?>[] result = new TypeVariable<?>[declaration.parameters.length];
        for (int i = 0; i < result.length; ++i) {
            Type value = type(declaration.parameters[i]);
            if (!(value instanceof TypeVariable<?>)) {
                throw new IllegalStateException("proof declaration parameter is not a variable");
            }
            result[i] = (TypeVariable<?>) value;
        }
        return result;
    }

    private static Type type(int id) {
        requireInstalled();
        if (id < 0 || id >= installed.types.length) throw new IllegalArgumentException("type node out of range: " + id);
        Type cached = types[id];
        if (cached != null) return cached;
        Node node = installed.types[id];
        switch (node.kind) {
        case CLASS:
            cached = requireClass(node.name);
            break;
        case PRIMITIVE:
            cached = primitive(node.descriptor);
            break;
        case VOID:
            cached = void.class;
            break;
        case ARRAY: {
            Type component = type(node.component);
            cached = component instanceof Class<?> ? Array.newInstance((Class<?>) component, 0).getClass()
                    : new GenericArrayTypeImpl(component);
            break;
        }
        case PARAMETERIZED:
            cached = new ParameterizedTypeImpl(type(node.raw), node.owner < 0 ? null : type(node.owner),
                    typeArray(node.arguments));
            break;
        case WILDCARD:
            cached = new WildcardTypeImpl(typeArray(node.upper), typeArray(node.lower));
            break;
        case VARIABLE: {
            TypeVariableImpl variable = new TypeVariableImpl(declaration(node.declaration), node.name);
            // Publish before bounds so recursive bounds resolve to this identity.
            types[id] = variable;
            variable.initialize(typeArray(node.bounds));
            return variable;
        }
        default:
            throw new IllegalStateException("unknown proof type kind: " + node.kind);
        }
        types[id] = cached;
        return cached;
    }

    private static GenericDeclaration declaration(int id) {
        Declaration record = declarationRecord(id);
        GenericDeclaration cached = declarations[id];
        if (cached != null) return cached;
        Class<?> owner = requireClass(record.owner);
        Object value;
        if (record.kind == CLASS_DECLARATION) {
            value = owner;
        } else if (record.kind == METHOD_DECLARATION) {
            Descriptor parsed = parseMethodDescriptor(record.descriptor);
            value = findMethod(owner, record.name, parsed.parameters, parsed.result);
        } else if (record.kind == CONSTRUCTOR_DECLARATION) {
            throw new IllegalStateException("constructor declarations are not in the DFU proof slice");
        } else {
            throw new IllegalStateException("unknown proof declaration kind: " + record.kind);
        }
        if (!(value instanceof GenericDeclaration)) {
            throw new IllegalStateException("proof declaration lacks GenericDeclaration: " + record.owner + "."
                    + record.name + record.descriptor);
        }
        cached = (GenericDeclaration) value;
        declarations[id] = cached;
        return cached;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] parameters, Class<?> result) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), parameters)
                    && method.getReturnType() == result) return method;
        }
        throw new IllegalStateException("proof generic method is unavailable: " + owner.getName() + "." + name);
    }

    private static Declaration declarationRecord(int id) {
        requireInstalled();
        if (id < 0 || id >= installed.declarations.length) throw new IllegalArgumentException("declaration out of range");
        return installed.declarations[id];
    }

    private static Type[] typeArray(int[] ids) {
        Type[] result = new Type[ids.length];
        for (int i = 0; i < ids.length; ++i) result[i] = type(ids[i]);
        return result;
    }

    private static Class<?> requireClass(String internalName) {
        Class<?> value = resolver.resolve(internalName);
        if (value == null || !internalName(value).equals(internalName)) {
            throw new IllegalStateException("proof resolver identity mismatch: " + internalName);
        }
        return value;
    }

    private static String internalName(Class<?> value) {
        return value.getName().replace('.', '/');
    }

    private static String descriptor(Class<?> type) {
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type.isArray()) return type.getName().replace('.', '/');
        return "L" + internalName(type) + ";";
    }

    private static String methodDescriptor(Method method) {
        StringBuilder result = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) result.append(descriptor(parameter));
        return result.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static Descriptor parseMethodDescriptor(String value) {
        if (value == null || value.length() < 3 || value.charAt(0) != '(') {
            throw new IllegalArgumentException("invalid proof method descriptor: " + value);
        }
        int[] index = { 1 };
        Class<?>[] parameters = new Class<?>[value.length()];
        int count = 0;
        while (value.charAt(index[0]) != ')') parameters[count++] = parseType(value, index);
        ++index[0];
        Class<?> result = parseType(value, index);
        if (index[0] != value.length()) throw new IllegalArgumentException("trailing descriptor data: " + value);
        return new Descriptor(Arrays.copyOf(parameters, count), result);
    }

    private static Class<?> parseType(String value, int[] index) {
        int start = index[0];
        char kind = value.charAt(index[0]++);
        if (kind == 'L') {
            int end = value.indexOf(';', index[0]);
            if (end < 0) throw new IllegalArgumentException("unterminated proof object descriptor");
            index[0] = end + 1;
            return requireClass(value.substring(start + 1, end));
        }
        if (kind == '[') return Array.newInstance(parseType(value, index), 0).getClass();
        return primitive(kind);
    }

    private static Class<?> primitive(char descriptor) {
        switch (descriptor) {
        case 'V': return void.class;
        case 'Z': return boolean.class;
        case 'B': return byte.class;
        case 'C': return char.class;
        case 'S': return short.class;
        case 'I': return int.class;
        case 'J': return long.class;
        case 'F': return float.class;
        case 'D': return double.class;
        default: throw new IllegalArgumentException("invalid proof primitive descriptor: " + descriptor);
        }
    }

    private static void validate(Table table) {
        for (Node node : table.types) {
            Objects.requireNonNull(node, "null proof type node");
            if (node.kind == VARIABLE) {
                range(node.declaration, table.declarations.length);
                if (node.name == null || node.bounds.length == 0) throw new IllegalArgumentException("invalid variable node");
            }
            if (node.kind == ARRAY) range(node.component, table.types.length);
            if (node.kind == PARAMETERIZED) {
                range(node.raw, table.types.length);
                optionalRange(node.owner, table.types.length);
            }
            ranges(node.arguments, table.types.length);
            ranges(node.upper, table.types.length);
            ranges(node.lower, table.types.length);
            ranges(node.bounds, table.types.length);
        }
        for (int i = 0; i < table.declarations.length; ++i) {
            Declaration declaration = Objects.requireNonNull(table.declarations[i], "null proof declaration");
            for (int parameter : declaration.parameters) {
                range(parameter, table.types.length);
                Node node = table.types[parameter];
                if (node.kind != VARIABLE || node.declaration != i) throw new IllegalArgumentException("declaration back-reference");
            }
        }
        for (ClassRecord record : table.classes) {
            Objects.requireNonNull(record, "null proof class record");
            range(record.declaration, table.declarations.length);
            optionalRange(record.superclass, table.types.length);
            ranges(record.interfaces, table.types.length);
        }
    }

    private static void ranges(int[] values, int limit) { for (int value : values) range(value, limit); }
    private static void optionalRange(int value, int limit) { if (value >= 0) range(value, limit); }
    private static void range(int value, int limit) {
        if (value < 0 || value >= limit) throw new IllegalArgumentException("proof metadata index out of range: " + value);
    }

    private static int[] copy(int[] values) { return values == null ? new int[0] : values.clone(); }
    private static <T> T[] copy(T[] values) { return Objects.requireNonNull(values).clone(); }
    private static void requireInstalled() { if (installed == null) throw new IllegalStateException("DFU proof metadata is not installed"); }

    private static final class Descriptor {
        final Class<?>[] parameters; final Class<?> result;
        Descriptor(Class<?>[] parameters, Class<?> result) { this.parameters = parameters; this.result = result; }
    }

    private static final class MemberKey {
        final byte kind; final String owner; final String name; final String descriptor;
        MemberKey(byte kind, String owner, String name, String descriptor) { this.kind = kind; this.owner = owner; this.name = name; this.descriptor = descriptor; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof MemberKey)) return false;
            MemberKey value = (MemberKey) other;
            return kind == value.kind && Objects.equals(owner, value.owner) && Objects.equals(name, value.name)
                    && Objects.equals(descriptor, value.descriptor);
        }
        @Override public int hashCode() { return (((kind * 31 + owner.hashCode()) * 31 + Objects.hashCode(name)) * 31) + Objects.hashCode(descriptor); }
        @Override public String toString() { return owner + "." + name + descriptor; }
    }

    private static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Type raw; private final Type owner; private final Type[] arguments;
        ParameterizedTypeImpl(Type raw, Type owner, Type[] arguments) {
            if (!(raw instanceof Class<?>)) throw new IllegalArgumentException("proof parameterized raw is not Class");
            this.raw = raw; this.owner = owner; this.arguments = arguments.clone();
        }
        @Override public Type[] getActualTypeArguments() { return arguments.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return owner; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType)) return false;
            ParameterizedType value = (ParameterizedType) other;
            return Objects.equals(owner, value.getOwnerType()) && raw.equals(value.getRawType())
                    && Arrays.equals(arguments, value.getActualTypeArguments());
        }
        @Override public int hashCode() { return Arrays.hashCode(arguments) ^ Objects.hashCode(owner) ^ raw.hashCode(); }
        @Override public String getTypeName() {
            String rawName = ((Class<?>) raw).getName();
            StringBuilder result;
            if (owner == null) result = new StringBuilder(rawName);
            else {
                Type ownerRaw = owner instanceof ParameterizedType ? ((ParameterizedType) owner).getRawType() : owner;
                if (!(ownerRaw instanceof Class<?>)) throw new IllegalStateException("proof owner raw is not Class");
                String ownerRawName = ((Class<?>) ownerRaw).getName();
                String nested = rawName.startsWith(ownerRawName + "$") ? rawName.substring(ownerRawName.length() + 1) : rawName;
                result = new StringBuilder(owner.getTypeName()).append('$').append(nested);
            }
            if (arguments.length != 0) {
                result.append('<');
                for (int i = 0; i < arguments.length; ++i) { if (i != 0) result.append(", "); result.append(arguments[i].getTypeName()); }
                result.append('>');
            }
            return result.toString();
        }
        @Override public String toString() { return getTypeName(); }
    }

    private static final class WildcardTypeImpl implements WildcardType {
        private final Type[] upper; private final Type[] lower;
        WildcardTypeImpl(Type[] upper, Type[] lower) { if (upper.length == 0 || lower.length > 1) throw new IllegalArgumentException("invalid proof wildcard"); this.upper = upper.clone(); this.lower = lower.clone(); }
        @Override public Type[] getUpperBounds() { return upper.clone(); }
        @Override public Type[] getLowerBounds() { return lower.clone(); }
        @Override public boolean equals(Object other) { return other instanceof WildcardType && Arrays.equals(upper, ((WildcardType) other).getUpperBounds()) && Arrays.equals(lower, ((WildcardType) other).getLowerBounds()); }
        @Override public int hashCode() { return Arrays.hashCode(upper) ^ Arrays.hashCode(lower); }
        @Override public String getTypeName() { return lower.length == 0 ? "? extends " + upper[0].getTypeName() : "? super " + lower[0].getTypeName(); }
        @Override public String toString() { return getTypeName(); }
    }

    private static final class GenericArrayTypeImpl implements GenericArrayType {
        private final Type component;
        GenericArrayTypeImpl(Type component) { this.component = Objects.requireNonNull(component); }
        @Override public Type getGenericComponentType() { return component; }
        @Override public boolean equals(Object other) { return other instanceof GenericArrayType && component.equals(((GenericArrayType) other).getGenericComponentType()); }
        @Override public int hashCode() { return component.hashCode(); }
        @Override public String getTypeName() { return component.getTypeName() + "[]"; }
        @Override public String toString() { return getTypeName(); }
    }

    private static final class TypeVariableImpl implements TypeVariable<GenericDeclaration> {
        private final GenericDeclaration declaration; private final String name; private Type[] bounds;
        TypeVariableImpl(GenericDeclaration declaration, String name) { this.declaration = Objects.requireNonNull(declaration); this.name = Objects.requireNonNull(name); }
        void initialize(Type[] bounds) { if (this.bounds != null || bounds.length == 0) throw new IllegalStateException("invalid proof variable initialization"); this.bounds = bounds.clone(); }
        @Override public Type[] getBounds() { if (bounds == null) throw new IllegalStateException("uninitialized proof variable"); return bounds.clone(); }
        @Override public GenericDeclaration getGenericDeclaration() { return declaration; }
        @Override public String getName() { return name; }
        @Override public String getTypeName() { return name; }
        @Override public AnnotatedType[] getAnnotatedBounds() { throw new UnsupportedOperationException("DFU proof type-use annotations are not installed"); }
        @Override public <T extends Annotation> T getAnnotation(Class<T> annotationClass) { Objects.requireNonNull(annotationClass); throw new UnsupportedOperationException("DFU proof type-parameter annotations are not installed"); }
        @Override public Annotation[] getAnnotations() { throw new UnsupportedOperationException("DFU proof type-parameter annotations are not installed"); }
        @Override public Annotation[] getDeclaredAnnotations() { throw new UnsupportedOperationException("DFU proof type-parameter annotations are not installed"); }
        @Override public boolean equals(Object other) { return other instanceof TypeVariable<?> && name.equals(((TypeVariable<?>) other).getName()) && declaration.equals(((TypeVariable<?>) other).getGenericDeclaration()); }
        @Override public int hashCode() { return declaration.hashCode() ^ name.hashCode(); }
        @Override public String toString() { return name; }
    }
}
