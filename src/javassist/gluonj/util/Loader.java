// Copyright (C) 2010 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.util;

import javassist.CtClass;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.CannotCompileException;
import java.lang.reflect.Method;
import javassist.gluonj.weave.Weaver;
import javassist.gluonj.weave.WeaveException;

/**
 * Class loader for running a GluonJ program.
 *
 * <p>For example, the following code calls <code>test.Main.main()</code>
 * with arguments given by <code>args</code>.  A reviser <code>test.Logger</code>
 * is applied to the program.
 *
 * <blockquote><pre>
 * String[] revisers = { "test.Logger" };
 * Object[] args = new Object[0];
 * Loader loader = new Loader(this.getClass().getClassLoader(), revisers);
 * loader.run("test.Main", args);
 * </pre></blockquote>
 *
 * @see javassist.Loader
 */
public class Loader extends javassist.Loader {
    private Weaver weaver;

    /**
     * Runs a GluonJ program.
     *
     * @param main      the main class.
     * @param args      arguments passed to <code>main()</code>.
     * @param revisers  the revisers applied to the program.
     */
    public static void run(Class<?> main, String[] args, Class<?>... revisers) throws Throwable {
        String[] names = new String[revisers.length];
        for (int i = 0; i < names.length; i++)
            names[i] = revisers[i].getName();

        Loader ld = new Loader(main.getClassLoader(), names);
        ld.run(main.getName(), args);
    }

    /**
     * Constructs a class loader.
     *
     * @param cl        a parent class loader.
     * @param revisers  reviser names.
     */
    public Loader(ClassLoader cl, String... revisers) throws Exception {
        super(cl, null);
        weaver = new Weaver(revisers, cl, false);
        ClassPool cp = weaver.getClassPool(); 
        setClassPool(cp);
        delegateLoadingOf("jdk.internal.");
        this.addTranslator(cp, new javassist.Translator() {
            public void start(ClassPool pool)
                throws NotFoundException, CannotCompileException {}
            public void onLoad(ClassPool pool, String classname)
                    throws NotFoundException, CannotCompileException {
                CtClass cc = pool.get(classname);
                try {
                    weaver.transform(cc);
                } catch (WeaveException e) {
                    throw new CannotCompileException(e);
                }
            }
        });
    }

    /**
     * Finds a method with the given name and parameter types.
     *
     * @param className         class name.
     * @param methodName        method name.
     * @param types             parameter types.
     */
    public Method getMethod(String className, String methodName, Class... types)
        throws NoSuchMethodException, ClassNotFoundException
    {
        return loadClass(className).getMethod(methodName, types);
    }
}
