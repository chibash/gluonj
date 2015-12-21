// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

public class Reviser {
    CtClass body;
    CtClass target;

    /**
     * @return      a reviser, or null if no target is specified. 
     */
    public static Reviser make(CtClass body, String optTarget, ClassConverter conv)
        throws NotFoundException, WeaveException
    {
        CtClass target = null;
        if (optTarget != null)
            target = body.getClassPool().get(optTarget);
        else if (body.isInterface()) {
            CtClass[] intfs = body.getInterfaces();
            if (intfs.length > 0)
                target = intfs[0];
        }
        else {
            CtClass sup = body.getSuperclass();
            if (!sup.getName().equals("java.lang.Object"))
                target = sup;
        }

        if (target == null)
            return null;    // maybe a reviser grouping other revisers.

        return new Reviser(target, body, conv);
    }

    private Reviser(CtClass target, CtClass body, ClassConverter conv)
        throws WeaveException, NotFoundException
    {
        this.body = body;
        this.target = target;
        readMethods(conv);
    }

    private static final String AT_WITHIN = javassist.gluonj.Within.class.getName();
    private static final String AT_WITHINCODE = javassist.gluonj.WithinCode.class.getName();
    private static final String AT_CODE = javassist.gluonj.Code.class.getName();

    private void readMethods(ClassConverter conv) throws WeaveException, NotFoundException {
        CtMethod[] ms = body.getDeclaredMethods();
        int num = ms.length;
        for (int i = 0; i < num; i++) {
            CtMethod cm = ms[i];
            Annotation[] anno = getAnnotations(cm);
            String withinValue = null;
            String withinCodeValue = null;
            String codeValue = null;
            if (anno != null)
                for (Annotation a: anno) {
                    String type = a.getTypeName();
                    if (AT_WITHIN.equals(type)) {
                        ClassMemberValue member = (ClassMemberValue)a.getMemberValue("value");
                        withinValue = member == null ? null : member.getValue();
                    }
                    else if (AT_WITHINCODE.equals(type)) {
                        StringMemberValue member = (StringMemberValue)a.getMemberValue("value");
                        withinCodeValue = member == null ? null : member.getValue();
                    }
                    else if (AT_CODE.equals(type)) {
                        StringMemberValue member = (StringMemberValue)a.getMemberValue("value");
                        codeValue = member == null ? null : member.getValue();
                    }
                }

            if (withinValue != null || withinCodeValue != null || codeValue != null)
                conv.recordMethod(body, target, cm, Predicate.make(cm, withinValue, withinCodeValue, codeValue));
        }
    }

    private static Annotation[] getAnnotations(CtMethod cm) {
        AnnotationsAttribute attr
        = (AnnotationsAttribute)cm.getMethodInfo2().getAttribute(AnnotationsAttribute.invisibleTag);
        if (attr != null)
            return attr.getAnnotations();
        else
            return null;
    }

    public String toString() {
        return "[@" + ReviserTree.AT_REVISER + " " + body.getName() + " revises " + target.getName() + "]";
    }

    public CtClass getTarget() { return target; }
    public CtClass getBody() { return body; }
    public String getName() { return body.getName(); }
}
