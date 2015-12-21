// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CtClass;
import javassist.bytecode.Opcode;
import javassist.bytecode.ConstPool;

/**
 * Redirects not-"proceed" calls.
 * It redirects a static method call to a call to the static
 * method that is declared in a reviser class and overrides the original one.
 * It also redirects a super.XXX() call from a subclass of the original class
 * to the corresponding method in the reviser class. 
 */
public class RedirectOthers extends RedirectProceed{

    public RedirectOthers(String orig, String superName, String reviser,
            String mname) {
        super(orig, superName, reviser, mname);
    }

    public boolean match(String enclosingClass, String method, String desc, String targetClass, int bytecode) {
        if (bytecode == Opcode.INVOKESTATIC || bytecode == Opcode.INVOKESPECIAL)
            return targetClass.equals(origClass);
        else
            return false;
    }

    public int getRealClassIndex(ConstPool cp, int oldIndex, CtClass enclosingClass,
                                 String targetClass, int op, String method) {
        return cp.addClassInfo(reviserClass);
    }
}
