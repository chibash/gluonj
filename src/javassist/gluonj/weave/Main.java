// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.io.File;
import java.util.ArrayList;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * A main class of the post-compile transformation.
 */
public class Main {
    /**
     * Executes post-compile transformation and writes transformed class files. 
     */
    public static void main(String[] args) throws Exception {
        ArrayList<String> files = new ArrayList<String>();
        String classPath = null;
        String outDir = ".";
        for (int i = 0; i < args.length; i++)
            if (args[i].equals("-debug"))
                Logger.active = true;
            else if (args[i].equals("-d") && i + 1 < args.length)
                outDir = args[++i];
            else if (args[i].equals("-cp") && i + 1 < args.length)
                classPath = args[++i];
            else
                files.add(args[i]);

        if (files.size() == 0)
            help();
        else if (files.size() > 0)
            try {
                compile(outDir, classPath, files.toArray(new String[files.size()]));
            }
            catch (WeaveException e) {
                System.err.println("Error: " + e.getMessage());
                if (Logger.active)
                    e.printStackTrace(System.err);
            }
            catch (NotFoundException e) {
                System.err.println("Error: (not found) " + e.getMessage());
                if (Logger.active)
                    e.printStackTrace(System.err);
            }
    }

    private static void help() {
        System.out.println("GluonJ runtime version 2.3");
        System.out.println("Copyright (C) 2009- Shigeru Chbia.  All rights reserved");
        System.out.println();
        System.out.println("Usage: java -jar gluonj.jar [-debug] [-d <dest dir>] [-cp <class path>] <class file> ...");
        System.out.println("Usage: java -javaagent:gluonj.jar=[debug:]<reviser>,<reviser>,... <main class>");
    }

    private static void compile(String outDir, String classPath, String[] fileNames)
        throws WeaveException, NotFoundException
    {
        ClassPool pool = new ClassPool();
        if (classPath == null)
            pool.appendClassPath(".");
        else
            pool.appendPathList(classPath);

        pool.appendSystemPath();
        ReviserTree tree = new ReviserTree();
        File base = new File(".");
        CtClass[] classes = new CtClass[fileNames.length];
        int i = 0;
        for (String fname: fileNames)
            classes[i++] = Weaver.readClass(pool, base, fname);

        ArrayList<String> reviserNames = new ArrayList<String>();
        for (CtClass c: classes)
            if (tree.append(c.getName(), c, false))
                reviserNames.add(c.getName());
            else
                c.detach();     // not @Rivser

        tree.addRequiringOrder(reviserNames.toArray(new String[reviserNames.size()]));
        Weaver weaver = new Weaver(tree, pool);
        weaver.setOutputDir(outDir);
        tree = null;
        for (String fname: fileNames)
            weaver.transformFile(base, fname);

        weaver.writeHelpers();
    }
}
