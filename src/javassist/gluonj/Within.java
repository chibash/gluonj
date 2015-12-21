// Copyright (C) 2009- Shigeru Chiba. All Rights Reserved.

package javassist.gluonj;

/**
 * Specifies that a method is effective only when 
 * it is called from within a given class. 
 */
public @interface Within {
    /**
     * A class object representing a caller class. 
     */
    Class value();
}
