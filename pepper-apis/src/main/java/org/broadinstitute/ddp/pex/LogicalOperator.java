package org.broadinstitute.ddp.pex;

import org.broadinstitute.ddp.pex.lang.PexParser;

enum LogicalOperator {
    AND, OR;

    Boolean apply(TreeWalkInterpreter.PexValueVisitor visitor, PexParser.ExprContext leftExpr, PexParser.ExprContext rightExpr) {
        Object lhs = leftExpr.accept(visitor);
        if (this == AND) {
            checkBoolValue("&&", "left", lhs);
        } else {
            checkBoolValue("||", "left", lhs);
        }

        // Short-circuit logical operation.
        Boolean value = (Boolean) lhs;
        if ((this == AND && !value) || (this == OR && value)) {
            return value;
        }

        Object rhs = rightExpr.accept(visitor);
        if (this == AND) {
            checkBoolValue("&&", "right", rhs);
        } else {
            checkBoolValue("||", "right", rhs);
        }

        return (Boolean) rhs;
    }

    private void checkBoolValue(String operator, String side, Object value) {
        if (!(value instanceof Boolean)) {
            String fmt = "Logical operator '%s' only supports bool values but %s-hand-side value is not an int value";
            throw new PexRuntimeException(String.format(fmt, operator, side));
        }
    }
}
