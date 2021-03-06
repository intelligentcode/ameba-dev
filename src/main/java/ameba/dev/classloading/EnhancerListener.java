package ameba.dev.classloading;

import ameba.dev.Enhancing;
import ameba.dev.classloading.enhancers.Enhanced;
import ameba.dev.classloading.enhancers.Enhancer;
import ameba.dev.classloading.enhancers.EnhancingException;
import ameba.event.Listener;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.glassfish.jersey.internal.util.PropertiesHelper.getValue;

/**
 * @author icode
 * @since 14-12-24
 */
public class EnhancerListener implements Listener<EnhanceClassEvent> {
    private static final Logger logger = LoggerFactory.getLogger(EnhancerListener.class);
    private static final
    String sp = "------------------------------------------------------------------------------------------------";

    private Map<String, Object> properties;

    public EnhancerListener(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public void onReceive(EnhanceClassEvent event) {
        ClassDescription desc = event.getClassDescription();
        if (desc == null) return;
        ClassPool classPool = Enhancer.getClassPool();
        CtClass clazz;
        try {
            clazz = classPool.makeClass(desc.getEnhancedByteCodeStream());
        } catch (IOException e) {
            throw new EnhancingException(e);
        }
        if (clazz.hasAnnotation(Enhanced.class)
                || clazz.isInterface()
                || clazz.getName().endsWith(".package")
                || clazz.getName().startsWith("jdk.")
                || clazz.getName().startsWith("java.")
                || clazz.getName().startsWith("javax.")
                || clazz.isEnum()
                || clazz.isPrimitive()
                || clazz.isAnnotation()
                || clazz.isArray()) {
            return;
        }
        logger.trace(sp);
        int i = 0;
        for (Enhancer enhancer : Enhancing.getEnhancers()) {
            enhance(enhancer, desc);
            if (getValue(properties, "ameba.module.dev.debug", false, null)) {
                try {
                    FileUtils.writeByteArrayToFile(new File(desc.getEnhancedClassFile().getPath() + "." + i),
                            desc.enhancedByteCode == null ? desc.classByteCode : desc.enhancedByteCode, false);
                } catch (IOException e) {
                    //noop
                }
                i++;
            }
        }
        try {
            clazz = Enhancer.makeClass(desc);
            Enhancer.addAnnotation(Enhancer.getAnnotations(clazz), Enhanced.class);
            desc.enhancedByteCode = clazz.toBytecode();
        } catch (IOException | CannotCompileException e) {
            logger.error("enhance err", e);
        }
        clazz.defrost();
        logger.trace(sp);
    }

    private void enhance(Enhancer enhancer, ClassDescription desc) {
        try {
            long start = System.currentTimeMillis();
            enhancer.enhance(desc);
            logger.trace("{}ms to apply {}[version: {}] to {}", System.currentTimeMillis() - start,
                    enhancer.getClass().getSimpleName(), enhancer.getVersion(), desc.className);
        } catch (Throwable e) {
            throw new EnhancingException("While applying " + enhancer + " on " + desc.className, e);
        }
    }
}
