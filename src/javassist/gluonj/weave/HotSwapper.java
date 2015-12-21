// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import javassist.gluonj.weave.WeaveException;

/**
 * Load-time weaver.
 */
public class HotSwapper {
    /**
     * Main method.
     *
     * <p>The argument is a list of the names of reviser classes.  If the weaver runs in
     * the debug mode, the argument is "debug:&lt;<i>reviser names ...</i>&gt;".
     *
     * <p>If the javassist.gluonj.classpath property is given, this weaver uses
     * the value of that property for obtaining a class file.  The value
     * of the property must be a CLASSPATH string separated by ':' (Linux etc.)
     * or ';' (Windows).
     */
    public static void premain(String args, Instrumentation inst)
        throws WeaveException
    {
        String[] revisers = parseArgs(args);
        if (revisers.length == 1 && revisers[0].length() < 1) {
            fatalError("no reviser specified");    // never returns.
            // throws a WeaveException.
        }

        inst.addTransformer(new Transformer(revisers));
    }

    private static String[] parseArgs(String args) {
        if (args == null)
            return null;

        final String debug = "debug:";
        Logger.active = args.startsWith(debug);
        if (Logger.active)
            args = args.substring(debug.length());

        return args.replace('/', '.').split(",");
    }

    /**
     * An event handler invoked when a new class file is being loaded.
     */
    public static class Transformer implements ClassFileTransformer {
        private String[] reviserNames;
        private Weaver weaver;
        private boolean stop;

        public Transformer(String[] revisers) {
            reviserNames = revisers;
            weaver = null;
            stop = false;
        }

        public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined,
                                ProtectionDomain domain, byte[] classfile)
            throws IllegalClassFormatException
        {
            if (stop)
                return null;

            if (weaver == null) {
                /* The system class loader for java.* and javax.*
                 * may not be able to find a glue class.  Hence,
                 * if loader is the system class loader, then
                 * this method returns without transformation.
                 */
                if (Weaver.isNonTransformable(className.replace('/', '.')))
                    return null;

                try {
                    weaver = new Weaver(reviserNames, loader, true);
                }
                catch (WeaveException e) {
                    stop = true;
                    showError(e);
                    return null;
                }
                catch (Throwable t) {
                    stop = true;
                    showError("while reading a reviser", t);
                    return null;
                }
            }

            try {
                return weaver.transformClass(className, classfile);
            }
            catch (WeaveException e) {
                stop = true;
                showError(e);
                return null;
            }
            catch (Throwable t) {
                String msg = "cannot transform a class: " + className.replace('/', '.');
                showError(msg, t);
                return null;
            }
        }

        private void showError(WeaveException e) {
            System.err.println("Error: " + e.getMessage());
            if (Logger.active)
                e.printStackTrace(System.err);
        }

        private void showError(String msg, Throwable e) {
            System.err.println("Error: " + msg);
            System.err.println("  by " + e);
            if (Logger.active)
                e.printStackTrace(System.err);
        }
    }

    private static void fatalError(String msg) {
        System.err.println("Fatal Error: " + msg);
        System.err.println("** exit **");
        System.exit(1);
    }
}
