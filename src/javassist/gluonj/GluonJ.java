// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Utility class.
 */
public class GluonJ {
    private GluonJ() {}

    /**
     * An identity function for type cast.
     * It returns the given argument as it is.
     */
    public static Object revise(Object obj) { return obj; }
}
