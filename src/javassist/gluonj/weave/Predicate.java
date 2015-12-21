// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.ClassPool;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import javassist.bytecode.ConstPool;
import javassist.gluonj.weave.WeaveException;
import java.util.List;

public abstract class Predicate {
    public static Predicate make(CtMethod cm, String withinValue, String withinCodeValue, String codeValue)
        throws WeaveException
    {
        if (withinValue == null)
            throw new WeaveException("@Code/@WithinCode without @Within: " + cm);

        Predicate pd;
        if (withinCodeValue == null && codeValue == null)
            pd = new PredicateWithin(cm, withinValue);
        else if (withinCodeValue != null && codeValue != null)
            throw new WeaveException("@Within with both @Code and @WithinCode: " + cm);
        else
            pd = new PredicateWithinCode(cm, withinValue, withinCodeValue, codeValue);

        return pd;
    }

    public boolean isFor(CtMethod cm) { return false; }
    public boolean isDuplicated() { return true; }
    public void setDuplicated(Predicate p) {}

    public abstract void appendTo(List<Predicate> list);
    public abstract boolean equiv(Predicate p);
    public abstract boolean match(String clazz, String method, String desc, String targetClass, int bytecode);
    public abstract boolean hasSameName();
    public abstract String getRealName();
    public abstract int getRealMethodIndex(ConstPool cp, int oldIndex, int oldTypeIndex);
    public abstract int getRealClassIndex(ConstPool cp, int oldIndex, CtClass enclosingClass,
                                          String targetClass, int op, String method) throws NotFoundException;

    /**
     * Returns false if the method is declared in an unmodifiable class such
     * as java.lang.Class and hence this predicate requires special implementation.
     */
    public abstract boolean callDirectly(ClassConverter.Method m);

    public abstract CtClass makeMarkerType(ClassConverter mt, ClassPool cpool, String descriptor)
        throws CannotCompileException;
}
