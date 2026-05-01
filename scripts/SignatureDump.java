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

        // Constructors
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        Arrays.sort(ctors, Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> k : ctors) {
            if (!Modifier.isPublic(k.getModifiers())) continue;
            methodEntries.add(dumpConstructor(k));
        }

        // Methods
        Method[] methods = c.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method m : methods) {
            if (!Modifier.isPublic(m.getModifiers())) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            // Skip Object overrides
            if (m.getDeclaringClass() == Object.class) continue;
            methodEntries.add(dumpMethod(m));
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
