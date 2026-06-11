package aurum.lang.compiler.backend.jvm;

import aurum.lang.compiler.backend.Translator;
import aurum.lang.compiler.frontend.stages.ProcessedType;

import java.util.HashMap;
import java.util.Map;

public final class ClassTranslator extends Translator<Class<?>> {
    private static final ByteClassLoader classLoader = new ByteClassLoader();
    private final JVMTranslator jvmTranslator;

    public ClassTranslator(ProcessedType type) {
        super(type);
        this.jvmTranslator = new JVMTranslator(type);
    }

    @Override
    public Translator<Class<?>> init() {
        ByteClassLoader.types.putIfAbsent(this.type.fullName(), this.jvmTranslator.translate());

        return this;
    }

    @Override
    public Class<?> translate() {
        return classLoader.defineClass(this.type.fullName());
    }

    static class ByteClassLoader extends ClassLoader {
        static final Map<String, byte[]> types = new HashMap<>();
        static final Map<String, Class<?>> definedTypes = new HashMap<>();

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (types.containsKey(name))
                return defineClass(name);

            return super.findClass(name);
        }

        public Class<?> defineClass(String fullName) {
            if (definedTypes.containsKey(fullName))
                return null;
            byte[] bytes = types.get(fullName);
            Class<?> definedClass = null;
            try {
                definedClass = defineClass(null, bytes, 0, bytes.length);
                return definedClass;
            } finally {
                if (definedClass != null)
                    definedTypes.put(fullName, definedClass);
            }
        }
    }
}
