// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.Opcode;
import javassist.bytecode.ConstPool;
import java.util.List;

/**
 * Redirects "proceed" calls, which are super.XXX() calls within reviser classes.
 * A "proceed" call is a static method call if a static method overrides
 * its super's method. 
 */
public class RedirectProceed extends Predicate {
    String origClass, reviserClass, superClass;
    private String methodName;

    /**
     * 
     * @param orig          the name of the original super class.
     * @param superName     the name of the new super class.
     * @param reviser       the name of the reviser.
     * @param mname         the method name.
     */
    public RedirectProceed(String orig, String superName, String reviser, String mname) {
        this.origClass = orig;
        this.reviserClass = reviser;
        this.methodName = mname;
        if (superName.equals(orig))
            this.superClass = null;
        else
            this.superClass = superName;
    }


    public void appendTo(List<Predicate> list) {
        list.add(this);
    }

    public boolean equiv(Predicate p) {
        if (p instanceof RedirectProceed) {
            RedirectProceed r = (RedirectProceed)p;
            return origClass.equals(r.origClass) && reviserClass.equals(r.reviserClass)
                   && methodName.equals(r.methodName);
        }
        else
            return false;
    }

    public boolean hasSameName() { return true; }

    public String getRealName() { return methodName; }

    public int getRealMethodIndex(ConstPool cp, int oldIndex, int oldTypeIndex) {
        return oldIndex;
    }

    public boolean match(String enclosingClass, String method, String desc, String targetClass, int bytecode) {
        if (bytecode == Opcode.INVOKESPECIAL || bytecode == Opcode.INVOKESTATIC)
            return enclosingClass.equals(reviserClass) && targetClass.equals(origClass);
        else
            return false;
    }

    public int getRealClassIndex(ConstPool cp, int oldIndex, CtClass enclosingClass,
                                 String targetClass, int op, String method) {
        if (superClass != null)
            return cp.addClassInfo(superClass);
        else
            return oldIndex;
    }

    public boolean callDirectly(ClassConverter.Method m) { return true; }

    public CtClass makeMarkerType(ClassConverter mt, ClassPool cpool, String descriptor)
        throws CannotCompileException
    {
        throw new CannotCompileException("fatal: unimplemented");
    }
}
