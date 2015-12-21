// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Specifies that a method is effective only when it is
 * called from a given method.  This annotation is used
 * together with @Within.
 */
public @interface WithinCode {
    /**
     * The method signature in the type-descriptor style.
     * For example, main([Ljava/lang/String;)
     */
    String value();
}
