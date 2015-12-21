// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CtMethod;
import javassist.bytecode.Descriptor;

public class PredicateWithinCode  extends PredicateWithin {
    private String methodName;  // @WithinCode/@Code
    private String descriptor;  // e.g. "(ILjava/lang/String;)".  no return type included.

    public PredicateWithinCode(CtMethod cm, String cname, String withinCodeValue, String codeValue) 
        throws WeaveException
    {
        super(cm, cname);
        if (codeValue != null)
            parseMethodName(codeValue);
        else if (withinCodeValue != null)
            parseMethodName2(withinCodeValue);
        else
            throw new WeaveException("no @WithinCode or @Code:" + cm);
    }

    public boolean equiv(Predicate p) {
        if (p instanceof PredicateWithinCode) {
            PredicateWithinCode pwc = (PredicateWithinCode)p;
            return equiv0(pwc)
                && methodName.equals(pwc.methodName)
                && descriptor.equals(pwc.descriptor);
        }
        else
            return false;
    }

    public boolean match(String clazz, String method, String desc,
                         String targetClass, int bytecode) {
        return super.match(clazz, method, desc, targetClass, bytecode)
               && methodName.equals(method) && desc.startsWith(descriptor);
    }

    /*
     * If the method signature is in the Java style,
     */
    private void parseMethodName(String name) throws WeaveException {
        int i = name.indexOf('(');
        if (i < 0) {
            methodName = name;
            descriptor = null;
        }
        else {
            methodName = name.substring(0, i);
            descriptor = parseParams(name, i + 1);
        }
    }

    /*
     * If the method signature is already in the form of type descriptor,
     */
    private void parseMethodName2(String name) throws WeaveException {
        int i = name.indexOf('(');
        if (i < 0) {
            methodName = name;
            descriptor = null;
        }
        else {
            methodName = name.substring(0, i);
            descriptor = name.substring(i);
        }
    }

    private String parseParams(String name, int i) throws WeaveException {
        StringBuilder desc = new StringBuilder();
        StringBuilder type = new StringBuilder();
        int c;
        int len = name.length();
        desc.append('(');
        while ((c = getNext(name, i++, len)) != ')') {
            if (c == ',') {
                if (type.length() < 1)
                    throw new WeaveException("bad syntax: " + name);

                toDescriptor(desc, type.toString());
                type = new StringBuilder();                
            }
            else if (c != ' ' && c != '\t')
                type.append((char)c);
        }

        if (type.length() > 0)
            toDescriptor(desc, type.toString());

        return desc.append(')').toString();
    }

    private static int getNext(String text, int i, int len) throws WeaveException {
        if (i < len)
            return text.charAt(i);
        else
            throw new WeaveException("bad syntax:" + text);
    }

    private static void toDescriptor(StringBuilder sbuf, String type) {
        int dim = 0;
        while (type.endsWith("[]")) {
            dim++;
            type = type.substring(0, type.length() - 2);
        }

        while (dim-- > 0)
            sbuf.append('[');

        sbuf.append(Descriptor.of(type));
    }
}

