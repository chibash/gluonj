// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import javassist.CtField;
import javassist.NotFoundException;

public class WeaveException extends Exception {
    private String where = null;

    public String getMessage() {
        String m = super.getMessage();
        if (where != null)
            m = where + ", " + m;

        return m;
    }

    public void setWhere(String w) { where = w; }

    public WeaveException(Exception e) {
        super(e.getMessage(), e);
    }

    public WeaveException(ClassNotFoundException e) {
        super("class not found: " + e.getMessage(), e);
    }

    public WeaveException(NotFoundException e) {
        super("not found: " + e.getMessage(), e);
    }

    public WeaveException(String m, Exception e) {
        super(m, e);
    }

    public WeaveException(String m) {
        super(m);
    }

    public WeaveException(String m, CtField f) {
        super(m + ": " + f.getName() + " in "
                + f.getDeclaringClass().getName());
    }
}
