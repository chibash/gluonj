// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.*;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javassist.CannotCompileException;
import javassist.gluonj.weave.WeaveException;
import java.util.HashMap;

/**
 * A driver for weaving.
 * It makes a HashMap<CtClass,ReviserTree.ReviserList> from the given revisers
 * and runs a ClassConverter.
 *
 * @see ClassConverter
 * @see ReviserTree
 */
public class Weaver {
    static int counter = 0;
    private ClassPool cpool;
    private String outputDir;       // used by transform(String,byte[])
    private ClassConverter converter;

    public static final String CLASSPATH_PROPERTY = "javassist.gluonj.classpath";
    public static final String OUTPUT_PROPERTY = "javassist.gluonj.output";

    /**
     * Constructs a weaver.
     */
    public Weaver(ReviserTree tree, ClassPool cp) throws WeaveException, NotFoundException {
        cpool = cp;
        converter = new ClassConverter();
        HashMap<CtClass,ReviserTree.ReviserList> revisers = tree.toMap(converter);
        initOutputDir();
        converter.prepare(revisers, cp);
    }

    /**
     * Constructs a weaver.
     *
     * @param loader        the class loader that is used for reading
     *                      original class files.
     * @param useClasspath  true if javassist.gluonj.classpath is effective.
     */
    public Weaver(String[] reviserNames, ClassLoader loader, boolean useClasspath)
        throws WeaveException
    {
        cpool = makeClassPool(loader, useClasspath);
        converter = new ClassConverter();
        try {
            ReviserTree tree = new ReviserTree();
            for (String name: reviserNames) {
                CtClass clazz = cpool.get(name);
                Logger.print("Reading.. " + name);
                tree.append(name, clazz, true);
            }

            tree.addRequiringOrder(reviserNames);
            HashMap<CtClass,ReviserTree.ReviserList> revisers = tree.toMap(converter);
            initOutputDir();
            converter.prepare(revisers, cpool);
        }
        catch (NotFoundException e) {
            throw new WeaveException(e);
        }
    }

    private static ClassPool makeClassPool(ClassLoader loader, boolean useClasspath) {
        ClassPool cp = new ClassPool(true);
        if (loader != null)
            cp.insertClassPath(new LoaderClassPath(loader));

        String pathlist = System.getProperty(CLASSPATH_PROPERTY);
        if (pathlist != null)
            try {
                cp.appendPathList(pathlist);
            }
            catch (NotFoundException e) {
                Logger.print("not found a jar file in "
                             + CLASSPATH_PROPERTY + ": " + e.getMessage());
            }

        return cp;
    }

    private void initOutputDir() {
        String out = System.getProperty(OUTPUT_PROPERTY);
        if (out == null)
            outputDir = ".";
        else
            outputDir = dirName(out);
    }

    public void setOutputDir(String dir) {
        outputDir = dirName(dir);
    }

    public ClassPool getClassPool() { return cpool; }

    // transformation

    /**
     * Transforms the specified class file.  It writes the modified file. 
     */
    public void transformFile(File baseDir, String fileName)
        throws WeaveException
    {
        CtClass original = readClass(cpool, baseDir, fileName);
        transform(original);
        try {
            original.writeFile(outputDir);
        }
        catch (CannotCompileException cce) { failedToWrite(original, cce); }
        catch (IOException cce) { failedToWrite(original, cce); }
    }

    public void writeHelpers() throws WeaveException {
        for (CtClass cc: converter.getHelpers())
            try {
                cc.writeFile(outputDir);
            }
            catch (CannotCompileException cce) { failedToWrite(cc, cce); }
            catch (IOException ioe) { failedToWrite(cc, ioe); }
    }

    public static CtClass readClass(ClassPool pool, File baseDir, String fileName)
        throws WeaveException
    {
        try {
            File f = new File(baseDir, fileName);
            BufferedInputStream is = new BufferedInputStream(new FileInputStream(f));
            CtClass clazz = pool.makeClassIfNew(is);
            is.close();
            return clazz;
        }
        catch (IOException ie) {
            throw new WeaveException("cannot read a class file: "
                                     + fileName, ie);
        }
    }

    private static void failedToWrite(CtClass cc, Exception e)
        throws WeaveException
    {
        throw new WeaveException("failed to write a class file: "
                                 + cc.getName(), e);
    }

    private static String dirName(String dir) {
        String path = dir.replace(File.separatorChar, '/');
        int len = path.length();
        if (path.charAt(len - 1) == '/')
            path = path.substring(0, len - 1);

        return path;
    }

    /**
     * Transforms a class file given as a byte array and returns the
     * modified array.
     * It returns null if no transformation is needed.
     */
    public byte[] transformClass(String className, byte[] classFile)
        throws WeaveException
    {
        className = toClassName(className);
        if (isNonTransformable(className))
            return null;

        try {
            /* If multiple class loaders use the same Weaver object
             * for reading a class file, the same class file might
             * be processed more than once.
             */
            CtClass clazz
                = cpool.makeClassIfNew(new ByteArrayInputStream(classFile));
            if (!clazz.isFrozen())
                transform(clazz);

            return clazz.toBytecode();
        }
        catch (IOException ie) {
            throw new WeaveException("cannot read a class file: "
                                     + className, ie);
        }
        catch (CannotCompileException cce) {
            throw new WeaveException("cannot transform a class file: "
                                     + className, cce);
        }
    }

    /**
     * Returns true if the given class name represents a system class.
     * Note that JVM does not accept transformed system classes.
     */
    protected static boolean isNonTransformable(String className) {
        return className.startsWith("java.")
            || className.startsWith("javax.")
            || className.startsWith("com.sun.")
            || className.startsWith("sun.")
            || className.startsWith("sunw.");
            // || className.startsWith("javassist.");
    }

    private static String toClassName(String jvmName) {
        return jvmName.replace('/', '.');
    }

    public void transform(CtClass orig) throws WeaveException {
        try {
            Logger.print("Transforming.. " + orig.getName());
            converter.revise(orig);
            Logger.dump(orig);
        }
        catch (NotFoundException e) {
            throw new WeaveException(e);
        } catch (CannotCompileException e) {
            throw new WeaveException(e);
        }
    }
}
