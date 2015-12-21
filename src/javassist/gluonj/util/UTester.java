package javassist.gluonj.util;

/**
 * A utility class for JUnit.
 */
public class UTester {
    /**
     * Run the current test method under the existence of revisers.
     * A new test-case object is created and the same test method is
     * invoked on that object.
     *
     * <p>A typical test method looks like this:</p>
     *
     * <blockquote><pre>
     * &#64;Test public void testRevisedCode() throws Throwable {
     *     if (UTester.runTestWith("some.Reviser")) return;
     *     assertEquals(100, doSomething());
     *         :
     * }
     * </blockquote></ul>
     *
     * <p>If your JUnit test runner calls this method, <code>runTestWith()</code>
     * creates a new instance of that test-case object and then
     * executes this <code>testRevisedCode()</code> method on that object
     * with a reviser named <code>some.Reviser</code>.  The test method calls
     * <code>runTestWith()</code> again but this time it returns false.
     * So the following <code>assertEquals()</code> is executed.
     * After the execution of <code>testRevisedCode()</code> with <code>some.Reviser</code>
     * finishes, <code>runTestWith()</code> finishes and returns false.
     * Then the original call to <code>testRevisedCode()</code> by JUnit immediately
     * finishes.</p> 
     *
     * @param revisers      fully-qualified reviser names.
     * @return false if this method is called again under the existence of revisers. 
     */
    public static boolean runTestWith(String... revisers) throws Throwable {
        if (UTester.class.getClassLoader().getClass().getName()
                .equals(Loader.class.getName()))
            return false;

        StackTraceElement trace = new Throwable().getStackTrace()[1];
        String methodName = trace.getMethodName();
        Object target = makeTarget(trace.getClassName(), revisers);
        invoke(target, methodName);
        return true;
    }

    /**
     * Makes an instance of the specified class under the existence of
     * reviers.  The class must have the default constructor.
     *
     * @param className         the class name
     * @param revisers          revisers names
     */
    public static Object makeTarget(String className, String... revisers) throws Exception {
        ClassLoader cl = new javassist.gluonj.util.Loader(UTester.class.getClassLoader(), revisers);
        Class<?> clazz = cl.loadClass(className);
        java.lang.reflect.Constructor<?> cons = clazz.getConstructor();
        return cons.newInstance();
    }

    /**
     * Invokes the specified method on the given target object.  The method
     * must not take any parameters.  
     */
    public static Object invoke(Object target, String methodName) throws Throwable {
        java.lang.reflect.Method m = target.getClass().getDeclaredMethod(methodName);
        try {
            return m.invoke(target);
        }
        catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
