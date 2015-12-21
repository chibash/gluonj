// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.util.List;
import javassist.CtClass;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.bytecode.Opcode;
import javassist.bytecode.MethodInfo;
import javassist.CannotCompileException;

import javassist.CtMethod;
import javassist.bytecode.ConstPool;
import javassist.Modifier;

public class PredicateWithin extends Predicate {
    private CtMethod method;    // predicate method
    private String realName;    // the name of METHOD after transformation.
    private boolean duplicated; // true if there is another Predicate with the same parameters.

    private String className;   // @Within

    /**
     * The interface type indicating the class has a revised method.
     * non-null if an unmodifiable class such as java.lang.Object declares
     * a method overridden by this method.  Otherwise, null.
     */
    private CtClass markerType;

    private static final String markerTypePackage
        = ClassConverter.class.getPackage().getName() + ".rt.";

    public PredicateWithin(CtMethod cm, String cname)
        throws WeaveException
    {
        method = cm;
        realName = ClassConverter.uniqueName(cm.getName());
        duplicated = false;
        className = cname;
        markerType = null;
    }

    public boolean isFor(CtMethod cm) {
        return method == cm;
    }

    public void appendTo(List<Predicate> list) {
        int len = list.size();
        for (int i = 0; i < len; i++)
            if (list.get(i) instanceof RedirectProceed) {
                list.add(i, this);
                return;
            }

        list.add(this);
    }

    public boolean equiv(Predicate p) {
        if (p instanceof PredicateWithin)
            return equiv0((PredicateWithin)p);
        else
            return false;
    }

    protected boolean equiv0(PredicateWithin p) {
        return className.equals(p.className);
    }

    public boolean match(String clazz, String method, String desc, String targetClass, int bytecode) {
        return className.equals(clazz);
    }

    public boolean hasSameName() { return false; }  // since realName is different from the original method's name.

    public String getRealName() { return realName; }

    public int getRealMethodIndex(ConstPool cp, int oldIndex, int oldTypeIndex) {
        return cp.addNameAndTypeInfo(cp.addUtf8Info(getRealName()), oldTypeIndex);        
    }

    public int getRealClassIndex(ConstPool cp, int oldIndex, CtClass enclosingClass,
                                 String targetClass, int op, String method) throws NotFoundException {
        if (op == Opcode.INVOKESPECIAL && !method.equals(MethodInfo.nameInit)
            && !targetClass.equals(enclosingClass.getName()))
            return cp.addClassInfo(enclosingClass.getSuperclass());

        return oldIndex;
    }  

    public boolean isDuplicated() {
        return duplicated;
    }

    public void setDuplicated(Predicate p) {
        realName = ((PredicateWithin)p).realName;
        duplicated = true;
    }

    public boolean callDirectly(ClassConverter.Method m) { return m.callDirectly(); }

    public CtClass makeMarkerType(ClassConverter mt, ClassPool cpool, String descriptor)
        throws CannotCompileException
    {
        if (markerType == null) {
            String methodName = realName;
            String typeName = markerTypePackage + 'I' + methodName;
            CtClass type = cpool.makeInterface(typeName);
            CtMethod m = CtMethod.make(new MethodInfo(type.getClassFile2().getConstPool(),
                                                      methodName, descriptor),
                                       type);
            m.setModifiers(Modifier.ABSTRACT);
            type.addMethod(m);
            mt.addHelper(type);
            markerType = type;
        }

        return markerType;
    }
}

