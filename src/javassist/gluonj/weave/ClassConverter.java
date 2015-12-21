// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import javassist.*;
import javassist.bytecode.Descriptor;

/**
 * ClassConverter is responsible for transforming all classes.
 * It works with a MethodConverter.
 */
public class ClassConverter {
    public static boolean inheritanceCheck = true;

    /**
     * A data structure representing a generic function.
     * It is a collection of all the methods having the same
     * name and signature, which may be declared in different
     * (totally unrelated) classes.
     */
    public static abstract class Method {
        private List<Predicate> predicates;

        Method() {
            predicates = new ArrayList<Predicate>();
        }

        Method(Method m) {
            if (m == null)
                predicates = new ArrayList<Predicate>();
            else
                predicates = m.predicates;
        }

        List<Predicate> getPredicates() { return predicates; }

        Predicate getPredicate(CtMethod cm) {
            for (Predicate p: predicates)
                if (p.isFor(cm))
                    return p;

            return null;
        }

        /**
         * Returns true if the actual type might inherit the method
         * from the revised class when the apparent type is the className type.
         * For example, it returns true if the apparent type is a super type
         * of the revised class.  It also returns true if the apparent type is
         * a sub type and it inherits the method from the revised class.
         *
         * @param className     the name of the apparent type.
         */
        public abstract boolean invokedOn(String className, ClassConverter mt) throws NotFoundException;

        public abstract boolean maybeInvokedOn(String className, ClassConverter mt) throws NotFoundException;

        /**
         * Records another reviser class that declares the method.
         * Any overriding method must call this.
         *
         * @param clazz     a reviser class.
         */
        public void append(CtClass clazz, Predicate pred) throws NotFoundException {
            for (Predicate p: predicates)
                if (pred.equiv(p)) {
                    pred.setDuplicated(p);
                    break;
                }

            pred.appendTo(predicates);
        }

        /**
         * Returns false if the method is declared in an unmodifiable class such
         * as java.lang.Class.  Such method needs special implementation.
         */
        public abstract boolean callDirectly();
    }

    /**
     * An interface method.
     */
    public static class IntfMethod extends Method {
        IntfMethod(Method m) {
            super(m);
        }

        public boolean invokedOn(String className, ClassConverter mt) throws NotFoundException {
            return true;
        }

        public boolean maybeInvokedOn(String className, ClassConverter mt) throws NotFoundException {
            return true;
        }

        public boolean callDirectly() { return true; }
    }

    /**
     * A method implemented in the standard way.
     */
    public static class StdMethod extends Method {
        private String methodName, descriptor;
        private ClassPool cpool;
        private boolean directlyCallable;

        /* map from a class name into:
         *          the declaring class of a reviser method
         *                   if the class declares or inherits the reviser method,
         *          IMPL     if the class declares a method overridden by a reviser method,
         *          IMPL_L   if the class declares a method overriding a reviser method,
         *          INHERIT  if the class inherits an IMPL (or reviser) method,
         *          null     if unknown, or
         *          NO       otherwise.
         */
        private HashMap<String,String> declarer;

        private static final String INHERIT = "*inherit*";
        private static final String IMPL = "*impl*";
        private static final String IMPL_L = "*implL*";
        private static final String NO = "*no*";

        /**
         * @param clazz     a reviser class.
         */
        public StdMethod(CtClass clazz, String name, String desc) throws NotFoundException {
            methodName = name;
            descriptor = desc;
            declarer = new HashMap<String,String>();
            cpool = clazz.getClassPool();
            directlyCallable = true;
            recordSuperTypes(clazz);
        }

        public boolean callDirectly() { return directlyCallable; }

        /**
         * Appends another class if it also declares the method.
         */
        public void append(CtClass clazz, Predicate p) throws NotFoundException {
            super.append(clazz, p);
            recordSuperTypes(clazz);
        }

        /**
         * Returns true if a revised method can be invoked.
         *
         * @param className     the static type of the receiver object.
         */
        public boolean invokedOn(String className, ClassConverter mt) throws NotFoundException {
            CtClass cc = cpool.get(className);
            String declName = invokedOn2(cc, mt);
            return declName != null && declName != IMPL_L;
        }

        /**
         * Returns true if a revised method can be invoked on the receiver object of
         * the static type that is either the given class or its super class. 
         *
         * @param className     the static type of the receiver object.
         */
        public boolean maybeInvokedOn(String className, ClassConverter mt) throws NotFoundException {
            CtClass cc = cpool.get(className);
            return invokedOn2(cc, mt) != null;
        }

        private String invokedOn2(CtClass cc, ClassConverter mt) throws NotFoundException {
            String className = cc.getName();
            String declName = declarer.get(className);
            if (declName == null) {
                CtClass superClass = cc.getSuperclass();
                if (superClass != null) {    // if superClass is not java.lang.Object
                    String newSuperName = mt.getNewClassName(superClass.getName());
                    if (newSuperName != null) {
                        CtClass superClass2 = cc.getClassPool().get(newSuperName);
                        if (!superClass2.subclassOf(cc))
                            superClass = superClass2;
                    }

                    declName = invokedOn2(superClass, mt);
                }

                if (declName == null) {
                    declarer.put(className, NO);
                    return null;
                }
                else if (declName == IMPL || declName == INHERIT || declaredIn(cc)) {
                    /* A sibling is a reviser class.
                     * A subclass is not a reviser class.
                     * If it is so, declarer.get(className) should return non null. 
                     */
                    declarer.put(className, IMPL_L);
                    return IMPL_L;
                }
                else {
                    declarer.put(className, declName);
                    return declName;
                }
            }
            else {
                if (declName == NO)
                    return null;
                else if (declName == INHERIT) {
                    declName = checkSuperClasses(cc.getSuperclass());
                    if (declName == null)
                        return INHERIT;
                    else
                        declarer.put(className, declName);
                }

                return declName;
            }
        }

        private boolean declaredIn(CtClass cc) {
            CtMethod[] methods = cc.getDeclaredMethods();
            String name = methodName;
            String desc = descriptor;
            for (CtMethod m: methods)
                if (m.getName().equals(name) && m.getSignature().equals(desc)) {
                    return true;
                }

            return false;
        }

        /**
         * Suppose that a class A extends B, which extends C.
         * A and C declare a method m().
         * First, if m() in C is revised, A is IMPL, B is INHERIT, and
         * C is "C".  Then, if m() in A is also revised, A changes to "A"
         * but B is still IMPL although B should be "A".
         *
         * checkSuperClasses() fixes this problem.
         */
        private String checkSuperClasses(CtClass cc) throws NotFoundException {
            if (cc == null)
                return null;
            else {
                String declName = declarer.get(cc.getName());
                // declName = null, NO, INHERIT, IMPL, or IMPL_L
                if (declName == NO)
                    return null;
                else if (declName == INHERIT) {
                    declName = checkSuperClasses(cc.getSuperclass());
                    if (declName != null && declName != INHERIT
                        && declName != IMPL && declName != IMPL_L)  // if declName is a class name
                        declarer.put(cc.getName(), declName);
                }

                return declName;
            }
        }

        /**
         * @param cc        a reviser class.
         */
        private void recordSuperTypes(CtClass cc) throws NotFoundException {
            /* cc must be a reviser class.
             * Note that a super class of that class might be another
             * reviser class.
             */
            recordSuperTypes2(cc.getSuperclass(), true);
            String cname = cc.getName();
            declarer.put(cname, cname);
        }

        private String recordSuperTypes2(CtClass cc, boolean isClass) throws NotFoundException {
            String found = declarer.get(cc.getName());
            // When recordSuperTypes() is called, declarer never contains NO.
            if (found == IMPL_L)
                return IMPL;
            else if (found != null)
                return found;

            String declName = null;
            CtMethod[] methods = cc.getDeclaredMethods();
            for (CtMethod m: methods)
                if (m.getName().equals(methodName) && m.getSignature().equals(descriptor)) {
                    declName = IMPL;    // implemented here.
                    break;
                }

            if (isClass) {
                CtClass superClass = cc.getSuperclass();
                if (superClass == null) {   // if superClass is java.lang.Object
                    if (declName != null)   // if declName == IMPL
                        directlyCallable = false;
                }
                else {
                    String declName2 = recordSuperTypes2(superClass, true);
                    if (declName == null)
                        if (declName2 == IMPL)
                            declName = INHERIT;
                        else
                            declName = declName2;
                }
            }

            CtClass[] interfaces = cc.getInterfaces();
            for (CtClass intf: interfaces) {
                String declName2 = recordSuperTypes2(intf, false);
                if (declName == null)
                    if (declName2 == IMPL)
                        declName = INHERIT;
                    else
                        declName = declName2;
            }

            if (declName != null)
                declarer.put(cc.getName(), declName);

            return declName;
        }
    }

    private MethodConverter methodConv;
    private HashMap<String,Method> methods;     // method names to Method objects
    private HashMap<String,String> classNames;  // original names to new names
    private HashMap<String,String> reviserNames;
    private HashMap<String,String> interfaceNames; // original names to new names
    private ArrayList<CtClass> helpers;         // implicitly generated helpers

    public ClassConverter() {
        methods = new HashMap<String,Method>();
        classNames = new HashMap<String,String>();
        interfaceNames = new HashMap<String,String>();
        reviserNames = new HashMap<String,String>();
        helpers = new ArrayList<CtClass>();
        methodConv = null;
    }

    public void recordNewClassName(String oldName, String newClassName) {
        classNames.put(oldName, newClassName);
    }

    /* If null is returned, the class with oldName is either
     * a reviser applied the last among the revisers
     * targeting the same class, or a normal class not revised.
     */
    public String getNewClassName(String oldName) {
        return classNames.get(oldName); 
    }

    public void recordNewInterfaceName(String oldName, String newName) {
        interfaceNames.put(oldName, newName);
    }

    public String getNewInterfaceName(String oldName) {
        return interfaceNames.get(oldName);
    }

    public void addReviser(String name) {
        reviserNames.put(name, name);
    }

    public boolean isReviser(String name) {
        return reviserNames.get(name) != null;
    }

    public void addHelper(CtClass cc) {
        helpers.add(cc);
    }

    public ArrayList<CtClass> getHelpers() {
        return helpers;
    }

    public Method lookupMethod(String methodName, String descriptor)
        throws NotFoundException
    {
        String key = methodName + ":" + descriptor;
        return methods.get(key);
    }

    public void recordMethod(CtClass reviser, CtClass target, CtMethod m, Predicate p) throws NotFoundException {
        String key = m.getName() + ":" + m.getSignature();
        Method found = methods.get(key);
        if (target.isInterface()) {
            found = new IntfMethod(found);
            methods.put(key, found);
        }
        else {
            if (found == null) {
                found = new StdMethod(reviser, m.getName(), m.getSignature());
                methods.put(key, found);
            }
        }

        if (p != null)
            found.append(reviser, p);
    }

    // transformation
    // the entry points are prepare() and revise().

    /**
      * Makes a MethodConverter that replaces the type of NEW with the
      * most specific reviser class.  If multiple revisers revise the same class,
      * their super classes are changed to make single inheritance hierarchy.
      */
     public void prepare(HashMap<CtClass,ReviserTree.ReviserList> allRevisers, ClassPool cp)
         throws WeaveException
     {
         MethodConverter conv = new MethodConverter(this);
         for (Map.Entry<CtClass,ReviserTree.ReviserList> e: allRevisers.entrySet()) {
             CtClass target = e.getKey();
             ReviserTree.ReviserList list = e.getValue();
             if (target.isInterface()) {
                 List<Reviser> revs = list.getList();
                 ArrayList<Reviser> intfs = new ArrayList<Reviser>();
                 ArrayList<Reviser> cls = new ArrayList<Reviser>();
                 for (Reviser r: revs) {
                     if (r.body.isInterface())
                         intfs.add(r);
                     else
                         cls.add(r);
                 }

                 if (intfs.size() > 0)
                     reorderInterfaceRevisers(target, intfs);

                 if (cls.size() > 0)
                     conv.replaceNewIClass(target, cls);
             }
             else
                 reorderRevisers(target, list.getList());
         }

         methodConv = conv;
     }

     /**
      * Modifies a given class.  prepare() has to be called in advance
      * before revise() is called.
      *
      * @param conv      the converter.
      */
     public void revise(CtClass clazz)
         throws WeaveException, NotFoundException, CannotCompileException
     {
         String newSuperName = getNewClassName(clazz.getSuperclass().getName());
         if (newSuperName != null && !isReviser(clazz.getName())) {
             CtClass cc = clazz.getClassPool().get(newSuperName);
             if (cc != clazz)
                 clazz.setSuperclass(cc);
         }

         if (!isReviser(clazz.getName()))
             reviseImplements(clazz);

         if (inheritanceCheck)
             checkInheritance(new HashMap<String,CtMethod>(), clazz);

         clazz.instrument(methodConv);
         methodConv.revise(clazz);
         for (CtMethod cm: clazz.getDeclaredMethods()) {
             ClassConverter.Method m = lookupMethod(cm.getName(), cm.getSignature());
             if (m != null) {
                 Predicate p = m.getPredicate(cm);
                 if (p == null)
                     reviseNormalMethod(clazz, cm, m);
                 else {
                     // cm is the method annotated with the predicate p.
                     revisePredicateMethod(clazz, cm, m, p);
                 }
             }
         }
     }

     void reviseImplements(CtClass clazz)
         throws WeaveException, NotFoundException
     {
         CtClass[] intfs = clazz.getInterfaces();
         boolean isAbstract = false;
         boolean modified = false;
         for (int i = 0; i < intfs.length; i++) {
             CtClass cc = intfs[i];
             String newName = getNewInterfaceName(cc.getName());
             if (newName != null) {
                 intfs[i] = clazz.getClassPool().get(newName);
                 modified = true;
                 isAbstract = isAbstractClass(clazz, intfs[i]);
             }
         }

         if (modified)
             clazz.setInterfaces(intfs);

         if (isAbstract) {
             int mod = clazz.getModifiers();
             if (!Modifier.isAbstract(mod))
                 clazz.setModifiers(mod | Modifier.ABSTRACT);
         }
     }

     boolean isAbstractClass(CtClass clazz, CtClass intf)
         throws NotFoundException
     {
         if (Modifier.isAbstract(clazz.getModifiers()))
             return true;

         if (intf != null && isReviser(intf.getName())) {
             for (CtMethod m: intf.getDeclaredMethods())
                 try {
                     clazz.getMethod(m.getName(), m.getSignature());
                 }
                 catch (NotFoundException e) {
                     return true;
                 }

             CtClass[] intfs = intf.getInterfaces();
             for (CtClass i: intfs) {
                 if (isAbstractClass(clazz, i))
                     return true;
             }
         }
         
         return false;
     }

     /**
      * Makes sure that all overriding methods are valid
      * with respect to their return types.
      */
     void checkInheritance(HashMap<String,CtMethod> hash, CtClass cc)
         throws WeaveException
     {
         try {
             CtClass[] ifs = cc.getInterfaces();
             int size = ifs.length;
             for (int i = 0; i < size; ++i)
                 checkInheritance(hash, ifs[i]);
         }
         catch (NotFoundException e) {}

         try {
             CtClass s = cc.getSuperclass();
             if (s != null)
                 checkInheritance(hash, s);
         }
         catch (NotFoundException e) {}

         CtMethod[] methods = cc.getDeclaredMethods();
         for (int i = 0; i < methods.length; i++) {
             CtMethod mth = methods[i];
             if (isOverridable(mth, cc)) {
                 String sig = mth.getName()
                            + Descriptor.getParamDescriptor(mth.getSignature());
                 CtMethod old = hash.put(sig, mth);
                 try {
                     if (old != null && !mth.getReturnType().subtypeOf(old.getReturnType()))
                         throw new WeaveException(mth.getLongName() + " wrongly overrides " + old.getLongName());
                 }
                 catch (NotFoundException nfe) {
                     new WeaveException(nfe);
                 }
             }
         }
     }

     private boolean isOverridable(CtMethod m, CtClass cc) {
         int mod = m.getModifiers();
         if (Modifier.isPrivate(mod))
             return false;
         else if (Modifier.isStatic(mod) && !isReviser(cc.getName()))
             return false;
         else if ((mod & javassist.bytecode.AccessFlag.BRIDGE) != 0)
             return false;
         else
             return true;
     }

     /**
      * Appends a new method that has the same name as (the real name of) the reviser method.
      * The appended method delegates to the original method specified by cm.  
      */
     private void reviseNormalMethod(CtClass target, CtMethod cm, ClassConverter.Method mm)
         throws NotFoundException, WeaveException
     {
         if (mm.maybeInvokedOn(target.getName(), this)) {
             List<Predicate> pred = mm.getPredicates();
             for (Predicate p: pred)
                 if (!p.isDuplicated())
                     try {
                         CtMethod m;
                         if (target.isInterface())
                             m = CtNewMethod.copy(cm, p.getRealName(), target, null);
                         else {
                             m = CtNewMethod.delegator(cm, target);
                             m.setName(p.getRealName());
                         }

                         target.addMethod(m);
                     } catch (CannotCompileException e) {
                         throw new WeaveException(e);
                     }
         }
     }

     /**
      * Renames a predicate method to avoid overriding a super's method.
      */
     private void revisePredicateMethod(CtClass clazz, CtMethod cm,
                                       ClassConverter.Method mm, Predicate p)
         throws CannotCompileException
     {
         cm.setName(p.getRealName());
         if (!mm.callDirectly()) {
             String descriptor = cm.getSignature();
             ClassPool cpool = clazz.getClassPool();
             for (Predicate pred: mm.getPredicates()) {
                 if (pred.isFor(cm))
                     clazz.addInterface(pred.makeMarkerType(this, cpool, descriptor));
             }
         }
     }

     private void reorderRevisers(CtClass target, List<Reviser> list)
         throws WeaveException
     {
         CtClass superReviser = target;
         CtClass last = list.get(list.size() - 1).body;
         for (Reviser a: list)
             try {
                 if (superReviser != last)
                     recordNewClassName(superReviser.getName(), last.getName());

                 CtClass cc = a.body;
                 addReviser(cc.getName());
                 CtClass origSuper = cc.getSuperclass();
                 if (origSuper != superReviser)
                     cc.setSuperclass(superReviser);

                 adjustInvokeSpecial(cc, origSuper, target, cc == last);
                 inheritConstructors(cc);
                 int mod = cc.getModifiers();
                 if (!Modifier.isPublic(mod))
                     cc.setModifiers(Modifier.setPublic(mod));

                 superReviser = cc;
             }
             catch (NotFoundException nfe) {
                 throw new WeaveException(nfe);
             } catch (CannotCompileException cce) {
                 throw new WeaveException(cce);
             }
     }

     public void reorderInterfaceRevisers(CtClass target, List<Reviser> list)
         throws WeaveException
     {
         CtClass superReviser = target;
         CtClass last = list.get(list.size() - 1).body;
         for (Reviser a: list)
             try {
                 if (superReviser != last)
                     recordNewInterfaceName(superReviser.getName(), last.getName());

                 CtClass cc = a.body;
                 addReviser(cc.getName());
                 if (!cc.subtypeOf(superReviser))
                     cc.addInterface(superReviser);

                 superReviser = cc;
             }
             catch (NotFoundException nfe) {
                 throw new WeaveException(nfe);
             }
     }

     public void copyAndReorderRevisers(CtClass target, List<Reviser> list)
         throws WeaveException
     {
         try {
             CtClass parent = target;
             Reviser last = list.get(list.size() - 1);
             CtClass lastCopy = makeCopy(last.body);
             for (Reviser a: list) {
                 /* The replacement appended below becomes effective soon.
                  * Since this method is called from TransformNewIClass.transform(),
                  * which is executed before TrnasformNewClass.transform(),
                  * the NEW instruction for instantiating the old class will be modified
                  * by TrnasformNewClass.transform().
                  */
                 recordNewClassName(parent.getName(), lastCopy.getName());
                 CtClass cc = a == last ? lastCopy : makeCopy(a.body);
                 addReviser(cc.getName());
                 CtClass origSuper = cc.getSuperclass();
                 cc.setSuperclass(parent);
                 adjustInvokeSpecial(cc, origSuper, target, cc == lastCopy);
                 inheritConstructors(cc);
                 int mod = cc.getModifiers();
                 if (!Modifier.isPublic(mod))
                     cc.setModifiers(Modifier.setPublic(mod));

                 parent = cc;
             }
         }
         catch (NotFoundException nfe) {
             throw new WeaveException(nfe);
         } catch (CannotCompileException cce) {
             throw new WeaveException(cce);
         }
     }

     private void adjustInvokeSpecial(CtClass cc, CtClass origSuper, CtClass target, boolean isLast)
         throws NotFoundException
     {
         String name = cc.getName();
         String origSuperName = origSuper.getName();
         String superName = cc.getSuperclass().getName();
         for (CtMethod m: cc.getDeclaredMethods()) {
             int mod = m.getModifiers();
             if (!Modifier.isPrivate(mod)) {
                 RedirectProceed r = new RedirectProceed(origSuperName, superName, name, m.getName());
                 recordMethod(cc, target, m, r);
                 if (isLast)
                     if (Modifier.isStatic(mod)) {
                         // RedirectStatic must be appended at the end.
                         RedirectOthers rs
                             = new RedirectOthers(origSuperName, superName, name, m.getName());
                         recordMethod(cc, target, m, rs);
                     }
             }
         }
     }

     private static int uniqueNo = 1;

     public static String uniqueName(String name) {
         return name + "_aop" + uniqueNo++;
     }

     private CtClass makeCopy(CtClass cc) throws NotFoundException {
         String newName = uniqueName(cc.getName());
         CtClass newClass = cc.getClassPool().getAndRename(cc.getName(), newName);
         newClass.setInterfaces(null);
         addHelper(newClass);
         return newClass;
     }

     /**
      * Makes constructors each of that calls the constructor of the super class
      * with the same signature.
      *
      * The given class must declare the default constructor although it can
      * declare other constructors.  If the class declares a constructor
      * receiving the same parameters for every constructor in the super class,
      * the class does not have to declare the default constructor.
      *
      * The body of the default constructor except a call to this() or super()
      * is executed when an object is instantiated by a constructor that is
      * not explicitly overridden by the reviser class.  So the default
      * constructor should call super(..) instead of this(..) because
      * the constructor never initializes the fields if this(..) is called
      * in the constructor.
      */
     private static void inheritConstructors(CtClass clazz)
         throws CannotCompileException, NotFoundException, WeaveException
     {
         String initName = clazz.makeUniqueName("_init");
         HashMap<String,CtConstructor> thisCons = getConstructors(clazz, initName);
         boolean noInitMethod = thisCons.get("()V") == null;
         String initCall = "{" + initName + "();}";
         CtClass superclazz = clazz.getSuperclass();
         CtConstructor[] superCons = superclazz.getDeclaredConstructors();

         boolean hasDefaultCons = false;
         for (int i = 0; i < superCons.length; ++i) {
             CtConstructor c = superCons[i];
             if (c.getSignature().equals("()V"))
                 hasDefaultCons = true;

             if (thisCons.get(c.getSignature()) == null) {
                 int mod = c.getModifiers();
                 if (Modifier.isPrivate(mod))
                     c.setModifiers(Modifier.setProtected(mod));

                 CtConstructor cons
                     = CtNewConstructor.make(c.getParameterTypes(),
                                             c.getExceptionTypes(), clazz);
                 if (noInitMethod)
                     throw new WeaveException("no default constructor: " + clazz.getName());

                 cons.insertAfter(initCall);
                 clazz.addConstructor(cons);
             }
         }

         if (hasDefaultCons || noInitMethod)
             return;

         CtConstructor dcons = thisCons.get("()V");
         clazz.removeConstructor(dcons);
     }

     /**
      * Returns a hash map from signatures to constructors.
      * This method also copies the default constructor to make an
      * initialization method with initName.  
      */
     private static HashMap<String,CtConstructor> getConstructors(CtClass clazz, String initName)
         throws CannotCompileException
     {
         CtConstructor[] tcons = clazz.getDeclaredConstructors();
         HashMap<String,CtConstructor> thisCons = new HashMap<String,CtConstructor>();
         for (int i = 0; i < tcons.length; i++) {
             String sig = tcons[i].getSignature();
             if (sig.equals("()V")) {
                 CtMethod mth = tcons[i].toMethod(initName, clazz, null);
                 mth.setModifiers(Modifier.PRIVATE);
                 clazz.addMethod(mth);
             }

             thisCons.put(tcons[i].getSignature(), tcons[i]);
         }

         return thisCons;
     }
}
