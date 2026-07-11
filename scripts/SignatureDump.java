// SignatureDump — load the signalwire SDK JAR via reflection and dump
// every public method/constructor signature as a JSON document.
//
// Usage:
//   javac scripts/SignatureDump.java -d build/scripts
//   java -cp build/scripts:build/libs/signalwire-sdk-2.0.0.jar:<runtime-deps> SignatureDump > raw_dump.json
//
// The Python wrapper (enumerate_signatures.py) consumes this JSON,
// applies the existing class/method-name translation tables from
// enumerate_surface.py, and emits the canonical port_signatures.json.
//
// Output shape (matches the .NET adapter's raw shape so per-port wrappers
// can stay symmetric):
//
//   {
//     "types": [
//       {
//         "package": "com.signalwire.sdk.agent",
//         "name": "AgentBase",
//         "kind": "class",
//         "methods": [
//           {
//             "name": "<init>" | "defineTool" | ... ,
//             "is_constructor": false,
//             "is_static": false,
//             "parameters": [
//               { "name": "name", "type": "java.lang.String" },
//               ...
//             ],
//             "return_type": "void" | "java.lang.String" | ...
//           },
//           ...
//         ]
//       }
//     ]
//   }

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SignatureDump {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: SignatureDump <path-to-jar>");
            System.exit(2);
        }
        String jarPath = args[0];

        URL[] urls = { new File(jarPath).toURI().toURL() };
        // Parent loader is system so JDK types resolve; SDK types come from
        // the URL classloader.
        URLClassLoader loader = new URLClassLoader(urls, SignatureDump.class.getClassLoader());

        List<String> classNames = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class")) continue;
                if (n.contains("$")) continue;            // skip nested / synthetic
                if (n.equals("module-info.class")) continue;
                String cls = n.substring(0, n.length() - ".class".length()).replace('/', '.');
                if (!cls.startsWith("com.signalwire")) continue;
                classNames.add(cls);
            }
        }
        Collections.sort(classNames);

        StringBuilder out = new StringBuilder();
        out.append("{\n  \"types\": [");
        boolean first = true;
        for (String cls : classNames) {
            Class<?> c;
            try {
                c = Class.forName(cls, false, loader);
            } catch (Throwable t) {
                System.err.println("skipping " + cls + ": " + t.getMessage());
                continue;
            }
            int mods = c.getModifiers();
            if (!Modifier.isPublic(mods)) continue;
            if (!first) out.append(",");
            first = false;
            out.append("\n    ").append(dumpType(c));
            // Walk public nested classes (Java idiom collapses related
            // resource classes into a single namespace file; cross-language
            // surface auditing needs each nested class as its own type entry
            // so it lines up with Python's class-per-file layout).
            for (Class<?> nested : collectNestedPublic(c)) {
                out.append(",");
                out.append("\n    ").append(dumpType(nested));
            }
        }
        out.append("\n  ]\n}\n");
        System.out.print(out.toString());
    }

    /**
     * Recursively collect every public nested class within {@code outer},
     * skipping nested annotations and the outer class itself.
     */
    static List<Class<?>> collectNestedPublic(Class<?> outer) {
        List<Class<?>> result = new ArrayList<>();
        try {
            Class<?>[] declared = outer.getDeclaredClasses();
            Arrays.sort(declared, Comparator.comparing(Class::getSimpleName));
            for (Class<?> nested : declared) {
                if (!Modifier.isPublic(nested.getModifiers())) continue;
                if (nested.isAnnotation()) continue;
                result.add(nested);
                result.addAll(collectNestedPublic(nested));
            }
        } catch (Throwable t) {
            // best-effort
        }
        return result;
    }

    /**
     * True when {@code c} is a strict subclass of the REST {@code ReadResource} base (i.e. inherits
     * its {@code paginate()} / {@code list()} / {@code get()}), but not {@code ReadResource} itself.
     * Detected by simple-name walk up the superclass chain to avoid a hard dependency on the class
     * being loadable by name.
     */
    static boolean extendsReadResource(Class<?> c) {
        for (Class<?> s = c.getSuperclass(); s != null && s != Object.class; s = s.getSuperclass()) {
            if ("ReadResource".equals(s.getSimpleName())
                    && s.getName().startsWith("com.signalwire.sdk.rest.")) {
                return true;
            }
        }
        return false;
    }

    static String dumpType(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        String pkg = c.getPackageName();
        String name = c.getSimpleName();
        String kind = c.isInterface() ? "interface"
                    : c.isEnum() ? "enum"
                    : c.isAnnotation() ? "annotation"
                    : "class";

        sb.append("{\n");
        sb.append("      \"package\": ").append(jsonString(pkg)).append(",\n");
        sb.append("      \"name\": ").append(jsonString(name)).append(",\n");
        sb.append("      \"kind\": ").append(jsonString(kind)).append(",\n");
        sb.append("      \"methods\": [");

        List<String> methodEntries = new ArrayList<>();

        // Constructors. Sort by param count, then by the full parameter-type
        // signature so overloaded ctors with the SAME count get a stable total
        // order — reflection's getDeclaredConstructors() order is unspecified
        // and varies run-to-run, which otherwise churns port_signatures.json.
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        Arrays.sort(
                ctors,
                Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount)
                        .thenComparing(SignatureDump::paramTypeSig));
        for (Constructor<?> k : ctors) {
            if (!Modifier.isPublic(k.getModifiers())) continue;
            methodEntries.add(dumpConstructor(k));
        }

        // Methods. Sort by name, then by the full parameter-type signature:
        // OVERLOADED methods share a name, and Comparator.comparing is stable,
        // so name-only ties keep getDeclaredMethods()'s order — which the JVM
        // does NOT guarantee across runs, making port_signatures.json
        // non-deterministic. The param-type tiebreaker gives a stable total order.
        Method[] methods = c.getDeclaredMethods();
        Arrays.sort(
                methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(SignatureDump::paramTypeSig));
        Set<String> declaredMethodNames = new java.util.HashSet<>();
        for (Method m : methods) {
            if (!Modifier.isPublic(m.getModifiers())) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            // Skip Object overrides
            if (m.getDeclaringClass() == Object.class) continue;
            declaredMethodNames.add(m.getName());
            methodEntries.add(dumpMethod(m));
        }

        // Inherited paginate(): the read/CRUD leaf resources (FaxLogs, VoiceLogs,
        // MessageLogs, VideoRoomSessions, FabricAddresses, ...) extend ReadResource
        // and inherit its paginate() but DECLARE only their constructor. Python's
        // reference enumerator surfaces inherited paginate() on those subclasses,
        // and the cross-port signature differ checks paginate per-method (it is not
        // a CRUD verb the crud_base binding covers). So project the inherited
        // ReadResource.paginate() onto every ReadResource subclass here, matching
        // how list/get are inherited — otherwise every read-resource leaf drifts on
        // a phantom "missing-port paginate". Only the no-arg overload is projected
        // (the oracle records paginate(self)); a subclass that overrides paginate
        // keeps its own declaration.
        if (extendsReadResource(c) && !declaredMethodNames.contains("paginate")) {
            try {
                Method pag = c.getMethod("paginate");
                methodEntries.add(dumpMethod(pag));
            } catch (Throwable ignored) {
                // best-effort: if paginate can't be resolved, skip.
            }
        }

        // Public fields — Python's reference adapter projects instance
        // attributes whose type is an SDK class as zero-arg accessor
        // methods (RestClient.fabric -> FabricNamespace). Mirror that for
        // Java's idiomatic ``public final FabricNamespace fabric;`` so
        // the cross-language audit lines up. Filtering to SDK classes
        // happens later in enumerate_signatures.py via the same rule.
        java.lang.reflect.Field[] fields = c.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(java.lang.reflect.Field::getName));
        for (java.lang.reflect.Field f : fields) {
            if (!Modifier.isPublic(f.getModifiers())) continue;
            if (f.isSynthetic()) continue;
            methodEntries.add(dumpField(f));
        }

        for (int i = 0; i < methodEntries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\n        ").append(methodEntries.get(i));
        }
        sb.append("\n      ]\n    }");
        return sb.toString();
    }

    /**
     * A stable, deterministic string of an executable's parameter types, used
     * ONLY as a sort tiebreaker so overloaded methods/constructors (same name /
     * same param count) get a fixed order independent of reflection's
     * unspecified getDeclared*() ordering. Not emitted into the output — purely
     * for sorting. Uses Type.getTypeName() for the full (generic-aware) spelling.
     */
    static String paramTypeSig(java.lang.reflect.Executable e) {
        StringBuilder sb = new StringBuilder();
        for (Type t : e.getGenericParameterTypes()) {
            sb.append(t.getTypeName()).append(',');
        }
        return sb.toString();
    }

    static String dumpConstructor(Constructor<?> k) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"<init>\",");
        sb.append("\"is_constructor\":true,");
        sb.append("\"is_static\":false,");
        sb.append("\"parameters\":").append(dumpParams(k.getParameters(), k.getGenericParameterTypes())).append(",");
        sb.append("\"return_type\":\"void\"");
        sb.append("}");
        return sb.toString();
    }

    static String dumpMethod(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":").append(jsonString(m.getName())).append(",");
        sb.append("\"is_constructor\":false,");
        sb.append("\"is_static\":").append(Modifier.isStatic(m.getModifiers())).append(",");
        sb.append("\"parameters\":").append(dumpParams(m.getParameters(), m.getGenericParameterTypes())).append(",");
        sb.append("\"return_type\":").append(jsonString(typeName(m.getGenericReturnType())));
        sb.append("}");
        return sb.toString();
    }

    // Synthesize a "method" entry from a public field so Python's
    // attribute-projection convention (RestClient.fabric) lines up with
    // Java's ``public final FabricNamespace fabric``.
    static String dumpField(java.lang.reflect.Field f) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":").append(jsonString(f.getName())).append(",");
        sb.append("\"is_constructor\":false,");
        sb.append("\"is_field\":true,");
        sb.append("\"is_static\":").append(Modifier.isStatic(f.getModifiers())).append(",");
        sb.append("\"parameters\":[],");
        sb.append("\"return_type\":").append(jsonString(typeName(f.getGenericType())));
        sb.append("}");
        return sb.toString();
    }

    static String dumpParams(Parameter[] params, Type[] genericTypes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            Parameter p = params[i];
            Type t = genericTypes[i];
            sb.append("{");
            sb.append("\"name\":").append(jsonString(p.getName())).append(",");
            sb.append("\"type\":").append(jsonString(typeName(t))).append(",");
            sb.append("\"is_varargs\":").append(p.isVarArgs());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    static String typeName(Type t) {
        if (t instanceof Class<?>) {
            Class<?> c = (Class<?>) t;
            if (c.isArray()) {
                return typeName(c.getComponentType()) + "[]";
            }
            return c.getName();
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            StringBuilder sb = new StringBuilder();
            sb.append(typeName(pt.getRawType()));
            sb.append("<");
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(typeName(args[i]));
            }
            sb.append(">");
            return sb.toString();
        }
        return t.getTypeName();
    }

    static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
