// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Specifies that a method is effective only when it is
 * called from a given method.  This annotation is used
 * together with @Within.
 */
public @interface Code {
    /**
     * The method signature in the Java style.
     * For example, main(java.lang.String[]).
     */
    String value();
}
