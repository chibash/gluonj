// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CtClass;

public class Logger {
    public static boolean active = false;
    public static String dumpDir = "gluonj.debug";

    public static void print(String s) {
        if (active) {
            System.err.println(s);
        }
    }

    public static void print(String header, Object[] objects) {
        if (!active)
            return;

        if (header != null)
            System.err.print(header);

        System.err.print("{");
        for (int i = 0; i < objects.length; i++) {
            System.err.print(objects[i]);
            System.err.print(", ");
        }

        System.err.println("}");
    }

    public static void dump(CtClass clazz) {
        if (active)
            clazz.debugWriteFile(dumpDir);
    }
}
