// Copyright (C) 2009 Shigeru Chiba. All Rights Reserved.

package javassist.gluonj.weave;

import java.util.List;
import javassist.expr.Expr;
import javassist.CtBehavior;

public class Hook {
    // returns null unless replacement is needed.
    public static String getCode(List<Hook> hooks, Expr clientExpr) {
        if (hooks.size() == 0)
            return null;

        String cond = null;
        for (Hook h: hooks) {
            String c = h.getCondition();
            if (c == null) {
                cond = null;
                break;
            }
            else if (cond == null)
                cond = c;
            else
                cond = cond + "|" + c;
        }

        String expr = "$$";
        CtBehavior w = clientExpr.where();
        expr = w.getDeclaringClass().getName() + ".class," + expr;
        if (cond == null)
            return "{$_=$proceed(" + expr + ");}"; 
        else
            return "if(" + cond + "){$_=$proceed(" + expr + ");}"; 
    }

    private String condition;

    public Hook() { condition = null; }

    public Hook(String cond) {
        condition = cond;
    }

    public String getCondition() { return condition; }

    public String getCode() { return "{$_=$proceed($$);}"; }
}
