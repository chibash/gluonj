// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Specifies required revisers.
 */
public @interface Require {
    /**
     * An array of class objects representing @Reviser classes.
     */
    Class[] value();
}
