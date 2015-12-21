// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.ArrayMemberValue;

public class ReviserTree {
    static final String AT_REVISER = javassist.gluonj.Reviser.class.getName();
    static final String AT_REQUIRE = javassist.gluonj.Require.class.getName();

    static class Node {
        String name;        // the name of this reviser
        CtClass body;
        ArrayList<Node> requires;
        boolean isRequired; // true if this is required by another reviser.
        Node groupTop;      // the reviser with the highest priority among nested ones in this reviser, or this reviser.
        String optTarget;   // the optional target name given by @Reviser.
        Node targetReviser; // non-null if the target is another reviser. 

        // these are set later during the topological sort.
        boolean visited, onStack;
        Reviser reviser;

        Node(String name) {
            this.name = name;
            this.body = null;
            this.requires = new ArrayList<Node>();
            this.isRequired = false;
            this.groupTop = this;
            this.optTarget = null;
            this.targetReviser = null;
            this.visited = false;
            this.onStack = false;
            this.reviser = null;
        }
    }

    private HashMap<String,Node> rootSet;           // reviser names to Node objects

    public ReviserTree() {
        rootSet = new HashMap<String,Node>();
    }

    /**
     * @param   name        the name of the given class.
     * @param   body        the declaration of the given class.
     * @param   eager       true if required revisers are immediately appended
     * @return              true if the given CtClass has @Reviser.
     */
    public boolean append(String name, CtClass body, boolean eager)
        throws WeaveException, NotFoundException
    {
        boolean newReviser;
        Node node = rootSet.get(name);
        if (node == null) {
            node = new Node(name);
            newReviser = true;
        }
        else {
            newReviser = false;
            if (node.body != null)
                return true;     // already appended.
        }

        ClassPool classPool = body.getClassPool();
        Annotation[] anno = getAnnotations(body);
        boolean reviser = false;
        MemberValue[] requiredClasses = null;
        if (anno != null) {
            node.body = body;
            for (Annotation a: anno) {
                String type = a.getTypeName();
                if (AT_REVISER.equals(type)) {
                    reviser = true;
                    ClassMemberValue member = (ClassMemberValue)a.getMemberValue("value");
                    node.optTarget = member == null ? null : member.getValue();
                }
                else if (AT_REQUIRE.equals(type)) {
                    ArrayMemberValue member = (ArrayMemberValue)a.getMemberValue("value");
                    requiredClasses = (MemberValue[])member.getValue();
                }
            }
        }

        if (reviser) {
            Node[] oldRequires = null;
            if (!newReviser)
                oldRequires = node.requires.toArray(new Node[node.requires.size()]);

            Node requiring = requireNestedClasses(classPool, node, eager);
            node.groupTop = requiring;

            /* If eager is false, groupTop is not set to a correct value.
             * So in Block A, a required reviser will be wrongly added to
             * the enclosing reviser.  Now add such a reviser to the groupTop. 
             */
            if (!newReviser && node != node.groupTop)  // if node has nested revisers
                copyRequired(classPool, oldRequires, node.groupTop);

            CtClass sup = getSuperType(body);
            if (isReviser(sup)) {
                Node t = addRequired(classPool, requiring, sup.getName(), eager);
                node.targetReviser = t;
            }

            if (requiredClasses != null) {
                // Block A
                for (int k = requiredClasses.length - 1; k >= 0; k--) {
                    MemberValue element = requiredClasses[k];
                    ClassMemberValue value = (ClassMemberValue)element;
                    String childName = value.getValue();
                    requiring = addRequired(classPool, requiring.groupTop, childName, eager);
                }
            }
        }

        if (newReviser)
            if (reviser)
                rootSet.put(name, node);
            else
                return false;   // not @Reviser
        else
            if (!reviser)
                throw new WeaveException(name + " must have @Reviser");

        return true;
    }

    private CtClass getSuperType(CtClass c) throws NotFoundException {
        if (c.isInterface()) {
            CtClass[] intfs = c.getInterfaces();
            if (intfs.length > 0)
                return intfs[0];
        }

        return c.getSuperclass();
    }

    private static void copyRequired(ClassPool classPool, Node[] required, Node groupTop) {
        for (Node reviser: required)
            if (!groupTop.requires.contains(reviser))
                groupTop.requires.add(reviser);
    }

    public void addRequiringOrder(String[] reviserNames)
        throws WeaveException, NotFoundException
    {
        int i = reviserNames.length;
        if (i < 2)
            return;

        Node parent = getNode(reviserNames[--i]);
        if (parent.body == null)
            throw new WeaveException("no body of " + reviserNames[i]);

        ClassPool pool = parent.body.getClassPool();
        while (--i >= 0)
            parent = addRequired(pool, parent.groupTop, reviserNames[i], false); 
    }

    private Node getNode(String name) throws NotFoundException {
        Node n = rootSet.get(name);
        if (n == null)
            throw new NotFoundException(name);
        else
            return n;
    }

    private Node addRequired(ClassPool pool, Node requiring, String className, boolean eager)
        throws WeaveException, NotFoundException
    {
        Node child = rootSet.get(className);
        if (child == null) {
            child = new Node(className);
            rootSet.put(className, child);
            if (eager)
                append(className, pool.get(className), true);
        }

        child.isRequired = true;
        requiring.requires.add(child);
        return child;
    }

    private Node requireNestedClasses(ClassPool pool, Node node, boolean eager)
        throws WeaveException, NotFoundException
    {
        CtClass[] nested = node.body.getNestedClasses();
        Node parent = node;
        for (int k = nested.length - 1; k >= 0; k--) {
            CtClass cc = nested[k];
            AnnotationsAttribute attr
                = (AnnotationsAttribute)cc.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
            if (attr != null && attr.getAnnotation(AT_REVISER) != null)
                if ((cc.getModifiers() & Modifier.STATIC) == 0)
                    throw new WeaveException("not-static nested class with @Reviser: " + cc.getName());
                else
                    parent = addRequired(pool, parent, cc.getName(), eager);
        }

        return parent;
    }

    private static Annotation[] getAnnotations(CtClass clazz) {
        AnnotationsAttribute attr
            = (AnnotationsAttribute)clazz.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
        if (attr != null)
            return attr.getAnnotations();
        else
            return null;
    }

    public static boolean isReviser(CtClass clazz) {
        Annotation[] anno = getAnnotations(clazz);
        if (anno != null)
            for (Annotation a: anno)
                if (AT_REVISER.equals(a.getTypeName()))
                    return true;

        return false;
    }

    
    public HashMap<CtClass,ReviserList> toMap(ClassConverter mt) throws WeaveException, NotFoundException {
        HashMap<CtClass,ReviserList> map = new HashMap<CtClass,ReviserList>();
        for (Node node: rootSet.values()) {
            if (!node.isRequired)    // if node is a root.
                toMap(map, node, mt, node);
        }

        return map;
    }

    private static void toMap(HashMap<CtClass,ReviserList> map, Node node, ClassConverter mt,
                               Node rootNode)
        throws WeaveException, NotFoundException
    {
        if (node.body == null)
            throw new WeaveException(node.name + " was not given");

        if (node.onStack)
            throw new WeaveException("the precedence order is cyclic: " + node.name);
        else
            node.onStack = true;

        if (node.visited) {
            node.onStack = false;
            return;
        }
        else
            node.visited = true;

        for (Node child: node.requires)
            toMap(map, child, mt, rootNode);

        node.onStack = false;
        if (node.optTarget == null && node.targetReviser != null) {
            Reviser t = node.targetReviser.reviser;
            if (t == null)
                throw new WeaveException("the precedence order is cyclic: "
                                         + node.name + ", " + node.targetReviser.name);

            node.optTarget = t.getTarget().getName();
        }

        Reviser r = Reviser.make(node.body, node.optTarget, mt);
        if (r == null)
            return;

        node.reviser = r;
        CtClass target = r.target;
        ReviserList list = map.get(target);
        if (list == null) {
            list = new ReviserList();
            list.add(r, node);
            map.put(target, list);
        }
        else
            list.add(r, node);
    }

    public static class ReviserList {
        private List<Reviser> list;

        public ReviserList() {
            list = new ArrayList<Reviser>();
        }

        public List<Reviser> getList() { return list; }

        public void add(Reviser r, Node node) throws WeaveException {
            if (!list.isEmpty()) {
                Reviser tail = list.get(list.size() - 1);
                int res = findTail(node, tail);
                if (res != FOUND)
                    throw new WeaveException(r.body.getName()
                            + " is required but the precedence order is ambiguous against "
                            + tail.body.getName());
            }

            list.add(r);
        }

        private static final int FOUND = 0;
        private static final int NOT_FOUND = 1;
        private static final int FAIL = 2;

        /* Visit all the children of start and returns:
         * FOUND if the tail is found.
         * FAIL: if other nodes sharing the same target are found
         *       but the tail is not found.
         *
         * This method is used to verify being total order.
         */
        private int findTail(Node start, Reviser tail) {
            int result = NOT_FOUND;
            for (Node child: start.requires) {
                // child.reviser is null if its target is never given.
                if (child.reviser == tail)
                    return FOUND;
                else if (child.reviser != null && child.reviser.target == tail.target)
                    result = FAIL;
                else {
                    int res = findTail(child, tail);
                    if (res == FAIL)
                        result = FAIL;
                    else if (res == FOUND)
                        return FOUND;
                }
            }

            return result;
        }
    }
}
