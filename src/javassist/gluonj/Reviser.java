// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Indicates that a class is a reviser.
 */
public @interface Reviser {
    /**
     * Not used any longer.
     */
    Class value() default Object.class;
}
