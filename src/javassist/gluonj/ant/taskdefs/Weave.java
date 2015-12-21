// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.ant.taskdefs;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.gluonj.weave.Weaver;
import javassist.gluonj.weave.Logger;
import javassist.gluonj.weave.ReviserTree;
import javassist.gluonj.weave.WeaveException;

/**
 * Ant task for compile-time weaving.
 */
public class Weave extends Task {
    private Path classpath;
    private ArrayList<FileSet> filesets;
    private String destdir;

    public Weave() {
        classpath = null;
        filesets = new ArrayList<FileSet>();
        destdir = ".";
    }

    public void setClasspath(Path path) {
        if (classpath == null)
            classpath = new Path(getProject());

        classpath.append(path);
    }

    public void setClasspathRef(Reference ref) {
        createClasspath().setRefid(ref);
    }

    public Path createClasspath() {
        if (classpath == null)
            classpath = new Path(getProject());

        return classpath.createPath();
    }

    public String[] getClasspath() {
        if (classpath != null)
            return classpath.list();

        return new String[0];
    }

    public void setDestdir(String dir) {
        destdir = dir;
    }

    public void setDebug(String value) {
        Logger.active
            = value.equals("yes") || value.equals("true")
              || value.equals("on");
    }

    public String getDestdir() {
        return destdir;
    }

    public void addFileset(FileSet fileset) {
        filesets.add(fileset);
    }

    /**
     * Returns a List<FileSet> object. 
     */
    public List<FileSet> getFileSets() { return filesets; }

    public void execute() throws BuildException {
        System.out.println("Weaving...");
        Exception ex = null;
        try {
            execute0();
            return;
        }
        catch (NotFoundException e) { ex = e; }
        catch (WeaveException e) { ex = e; }
        catch (RuntimeException e) { ex = e; }
        if (Logger.active)
            ex.printStackTrace();

        String msg = ex.getMessage();
        if (msg == null)
            msg = ex.toString();

        throw new BuildException(msg, ex);
    }

    protected void execute0() throws BuildException, WeaveException, NotFoundException {
        ClassPool pool = makeClassPool();
        ReviserTree tree = new ReviserTree();
        for (FileSet fs: filesets) {
            DirectoryScanner ds
                = fs.getDirectoryScanner(getProject());
            String[] files = ds.getIncludedFiles();
            File base = ds.getBasedir();
            for (String fname: files) {
                CtClass c = Weaver.readClass(pool, base, fname);
                if (!tree.append(c.getName(), c, false))
                    c.detach();     // not @Rivser
            }
        }
        
        String dest = getDestdir();
        Weaver weaver = new Weaver(tree, pool);
        weaver.setOutputDir(dest);
        tree = null;
        for (FileSet fs: filesets) {
            DirectoryScanner ds
                = fs.getDirectoryScanner(getProject());
            String[] files = ds.getIncludedFiles();
            File base = ds.getBasedir();
            for (String fname: files)
                weaver.transformFile(base, fname);
        }

        weaver.writeHelpers();
    }

    private ClassPool makeClassPool() throws NotFoundException {
        ClassPool cp = new ClassPool(true);
        String[] path = getClasspath();
        if (path != null)
            for (int i = 0; i < path.length; i++) {
                String dir = dirName(path[i]);
                cp.appendClassPath(dir);
            }

        return cp;
    }

    private static String dirName(String dir) {
        String path = dir.replace(java.io.File.separatorChar, '/');
        int len = path.length();
        if (path.charAt(len - 1) == '/')
            path = path.substring(0, len - 1);

        return path;
    }
}
