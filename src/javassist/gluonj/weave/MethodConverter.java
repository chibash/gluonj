// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.util.HashMap;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CodeConverter;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.convert.Transformer;


/**
 * MethodConverter is responsible for transforming classes.
 * It works with ClassConverter.
 */
public class MethodConverter extends CodeConverter {
    private TransformNewClass newTransformer;
    private TransformNewIClass newIfaceTransformer;

    public MethodConverter(ClassConverter mt) {
        TransformCall tc = new TransformCall(transformers, mt);
        newTransformer = new TransformNewClass(tc, mt);
        transformers = newIfaceTransformer
                     = new TransformNewIClass(newTransformer, mt);
    }

    /**
     * Suppose that a class implements an interface targeted by revisers. This
     * method revises that class when the code converter first finds an
     * expression for creating an instance of that class.
     */
    public void replaceNewIClass(CtClass target, List<Reviser> revisers) {
        newIfaceTransformer.record(target, revisers);
    }

    /**
     * This revise() calls revise() on all registered TransformNewIClass
     * objects. If the target is an interface, this revise() does not call
     * anything. It bridges between ClassConverter.revise() and
     * TransformNewIClass.revise().
     */
    public void revise(CtClass target) throws NotFoundException, WeaveException {
        if (!target.isInterface()) {
            CtClass[] intfs = target.getInterfaces();
            newIfaceTransformer.revise(target, intfs);
        }
    }

    /**
     * Transforming call expressions.
     */
    static public class TransformCall extends Transformer {
        private ClassConverter conv;

        private MethodInfo current;

        public TransformCall(Transformer next, ClassConverter mt) {
            super(next);
            conv = mt;
        }

        public void initialize(ConstPool cp, CtClass clazz, MethodInfo minfo)
            throws CannotCompileException
        {
            current = minfo;
        }

        public int transform(CtClass clazz, int pos, CodeIterator iterator, ConstPool cp)
            throws CannotCompileException
        {
            int c = iterator.byteAt(pos);
            if (c == INVOKEINTERFACE || c == INVOKESPECIAL || c == INVOKESTATIC
                    || c == INVOKEVIRTUAL) {
                int index = iterator.u16bitAt(pos + 1);
                int nt;
                String className;
                if (c == INVOKEINTERFACE) {
                    nt = cp.getInterfaceMethodrefNameAndType(index);
                    className = cp.getInterfaceMethodrefClassName(index);
                }
                else {
                    nt = cp.getMethodrefNameAndType(index);
                    className = cp.getMethodrefClassName(index);
                }

                int ntd = cp.getNameAndTypeDescriptor(nt);
                String method = cp.getUtf8Info(cp.getNameAndTypeName(nt));
                String desc = cp.getUtf8Info(ntd);
                try {
                    ClassConverter.Method m = conv.lookupMethod(method, desc);
                    // check here whether the className is a NewIClass
                    if (m != null && m.invokedOn(className, conv)) {
                        String enclosingClass = clazz.getName();
                        String curName = current.getName();
                        String curDesc = current.getDescriptor();
                        int ci;
                        if (c == INVOKEINTERFACE)
                            ci = cp.getInterfaceMethodrefClass(index);
                        else
                            ci = cp.getMethodrefClass(index);

                        String targetClass = cp.getClassInfo(ci);
                        for (Predicate p : m.getPredicates())
                            if (p.match(enclosingClass, curName, curDesc, targetClass, c)) {
                                int nt2 = p.getRealMethodIndex(cp, nt, ntd);
                                if (p.callDirectly(m)) {
                                    int ci2 = p.getRealClassIndex(cp, ci,
                                            clazz, targetClass, c, method);
                                    if (ci != ci2 || nt != nt2) {
                                        int newIndex;
                                        if (c == INVOKEINTERFACE)
                                            newIndex = cp.addInterfaceMethodrefInfo(ci2, nt2);
                                        else
                                            newIndex = cp.addMethodrefInfo(ci2, nt2);

                                        iterator.write16bit(newIndex, pos + 1);
                                    }
                                }
                                else {
                                    CtClass markerType = p.makeMarkerType(conv, clazz.getClassPool(), desc);
                                    transformIf(markerType.getName(), nt, ntd, desc, iterator, pos, c, cp, p);
                                }

                                break;
                            }
                    }
                }
                catch (NotFoundException e) {
                    throw new CannotCompileException(e);
                }
                catch (BadBytecode bb) {
                    throw new CannotCompileException(bb);
                }
            }

            return pos;
        }

        private void transformIf(String markerType, int nameAndTypeIndex, int descIndex,
                                 String desc, CodeIterator iterator, int pos, int opcode,
                                 ConstPool cp, Predicate pred)
            throws CannotCompileException, BadBytecode
        {
            int nargs = javassist.bytecode.Descriptor.paramSize(desc);
            int markerIndex = cp.addClassInfo(markerType);
            int origPos = pos + iterator.insertGap(pos, nargs == 1 ? 21 : 18);

            if (nargs == 1)
                iterator.writeByte(SWAP, pos++);
            else if (nargs > 0)
                throw new CannotCompileException("cannot advise the method: "
                        + cp.getUtf8Info(cp.getNameAndTypeName(nameAndTypeIndex))
                        + ":" + desc);

            iterator.writeByte(DUP, pos);
            iterator.writeByte(INSTANCEOF, pos + 1);
            iterator.write16bit(markerIndex, pos + 2);
            iterator.writeByte(IFEQ, pos + 4); // if false
            int jumpPos = pos + 4;

            iterator.writeByte(CHECKCAST, pos + 7);
            iterator.write16bit(markerIndex, pos + 8);
            pos += 10;
            if (nargs == 1)
                iterator.writeByte(SWAP, pos++);

            iterator.writeByte(INVOKEINTERFACE, pos);
            int index = cp.addInterfaceMethodrefInfo(markerIndex,
                           pred.getRealMethodIndex(cp, nameAndTypeIndex, descIndex));
            iterator.write16bit(index, pos + 1);
            iterator.write16bit((nargs + 1) << 8, pos + 3);
            iterator.writeByte(GOTO, pos + 5);
            int codeSize = opcode == INVOKEINTERFACE ? 5 : 3;
            iterator.write16bit(origPos + codeSize - (pos + 5), pos + 6);
            pos += 8;
            iterator.write16bit(pos - jumpPos, jumpPos + 1);
            if (nargs == 1)
                iterator.writeByte(SWAP, pos);
        }

        public int extraStack() { return 1; }
    }

    /*
     * a copy from javassist.convert.TransformNewClass.
     */
    public static class TransformNewClass extends Transformer {
        private int nested;
        private ClassConverter conv;

        public TransformNewClass(Transformer next, ClassConverter mt) {
            super(next);
            conv = mt;
        }

        public void initialize(ConstPool cp, CodeAttribute attr) {
            nested = 0;
        }

        public int transform(CtClass clazz, int pos, CodeIterator iterator,
                             ConstPool cp)
            throws CannotCompileException
        {
            int index;
            int c = iterator.byteAt(pos);
            if (c == NEW) {
                index = iterator.u16bitAt(pos + 1);
                String newName = conv.getNewClassName(cp.getClassInfo(index));
                if (newName != null) {
                    if (iterator.byteAt(pos + 3) != DUP)
                        throw new CannotCompileException(
                                "NEW followed by no DUP was found");

                    iterator.write16bit(cp.addClassInfo(newName), pos + 1);
                    ++nested;
                }
            }
            else if (c == INVOKESPECIAL && nested > 0) {
                index = iterator.u16bitAt(pos + 1);
                int nt = cp.getMemberNameAndType(index);
                if (MethodInfo.nameInit.equals(cp.getUtf8Info(cp.getNameAndTypeName(nt)))) {
                    String klass = cp.getClassInfo(cp.getMemberClass(index));
                    String newName = conv.getNewClassName(klass);
                    if (newName != null) {
                        int newIndex = cp.addMethodrefInfo(cp.addClassInfo(newName), nt);
                        iterator.write16bit(newIndex, pos + 1);
                        --nested;
                    }
                }
            }
            else if (c == LDC)
                transformLdc(pos, iterator, cp);
            else if (c == LDC_W)
                transformLdcW(pos, iterator, cp);

            return pos;
        }

        private void transformLdc(int pos, CodeIterator iterator, ConstPool cp)
            throws CannotCompileException
        {
            int index = iterator.byteAt(pos + 1);
            if (cp.getTag(index) == ConstPool.CONST_Class) {
                String newName = conv.getNewClassName(cp.getClassInfo(index));
                if (newName != null) {
                    int index2 = cp.addClassInfo(newName);
                    if (index2 < 0x100)
                        iterator.writeByte(index2, pos + 1);
                    else
                        try {
                            iterator.insertGap(pos, 1);
                            iterator.writeByte(LDC_W, pos);
                            iterator.write16bit(index2, pos + 1);
                        }
                        catch (BadBytecode bb) {
                            throw new CannotCompileException(bb);
                        }
                }
            }
        }
        private void transformLdcW(int pos, CodeIterator iterator, ConstPool cp)
            throws CannotCompileException
        {
            int index = iterator.u16bitAt(pos + 1);
            if (cp.getTag(index) == ConstPool.CONST_Class) {
                String newName = conv.getNewClassName(cp.getClassInfo(index));
                if (newName != null) {
                    int index2 = cp.addClassInfo(newName);
                    iterator.write16bit(index2, pos + 1);
                }
            }
        }
    }

    /**
     * This handles a revise class targeting an interface type.
     */
    static public class TransformNewIClass extends Transformer {
        private ClassConverter conv;
        private HashMap<CtClass, List<Reviser>> interfaceNames;

        /**
         * Transforms a NEW expression (object creation).
         */
        public TransformNewIClass(Transformer next, ClassConverter mt) {
            super(next);
            conv = mt;
            interfaceNames = new HashMap<CtClass, List<Reviser>>();
        }

        public void record(CtClass targetInterface, List<Reviser> revisers) {
            interfaceNames.put(targetInterface, revisers);
        }

        public int transform(CtClass clazz, int pos, CodeIterator iterator, ConstPool cp)
            throws CannotCompileException
        {
            int index;
            int c = iterator.byteAt(pos);
            if (c == NEW)
                try {
                    index = iterator.u16bitAt(pos + 1);
                    CtClass newClass = clazz.getClassPool().get(
                            cp.getClassInfo(index));
                    CtClass[] intfs = newClass.getInterfaces();
                    revise(newClass, intfs);
                }
                catch (NotFoundException nfe) {
                    throw new CannotCompileException(nfe);
                }
                catch (WeaveException we) {
                    throw new CannotCompileException(we);
                }

            return pos;
        }

        /**
         * This method finds a class that implements a revised interface and
         * processes it. This method is called when a class is loaded (from
         * ClassConverter.revise()) or when the name of the class is found in
         * bytecode (by a MethodConverter, i.e. this TransformNewIClass).
         */
        public void revise(CtClass target, CtClass[] interfaces)
            throws WeaveException
        {
            String found = conv.getNewClassName(target.getName());
            if (found == null) // unless the class has been already modified.
                for (CtClass iface : interfaces) {
                    List<Reviser> revisers = interfaceNames.get(iface);
                    if (revisers != null)
                        conv.copyAndReorderRevisers(target, revisers);
                }
        }
    }
}
