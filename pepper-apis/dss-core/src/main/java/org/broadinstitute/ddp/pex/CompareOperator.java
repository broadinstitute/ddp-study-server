package org.broadinstitute.ddp.pex;

import java.time.LocalDate;

enum CompareOperator {
    LESS, LESS_EQ, GREATER, GREATER_EQ, EQ, NOT_EQ;

    boolean apply(Object left, Object right) {
        switch (this) {
            case LESS:
                checkIntOrDateValues("<", left, right);
                if (left instanceof Long) {
                    return (long) left < (long) right;
                } else {
                    return ((LocalDate) left).isBefore((LocalDate) right);
                }
            case LESS_EQ:
                checkIntOrDateValues("<=", left, right);
                if (left instanceof Long) {
                    return (long) left <= (long) right;
                } else {
                    LocalDate lhs = (LocalDate) left;
                    LocalDate rhs = (LocalDate) right;
                    return lhs.isBefore(rhs) || lhs.isEqual(rhs);
                }
            case GREATER:
                checkIntOrDateValues(">", left, right);
                if (left instanceof Long) {
                    return (long) left > (long) right;
                } else {
                    return ((LocalDate) left).isAfter((LocalDate) right);
                }
            case GREATER_EQ:
                checkIntOrDateValues(">=", left, right);
                if (left instanceof Long) {
                    return (long) left >= (long) right;
                } else {
                    LocalDate lhs = (LocalDate) left;
                    LocalDate rhs = (LocalDate) right;
                    return lhs.isAfter(rhs) || lhs.isEqual(rhs);
                }
            case EQ:
                return left.equals(right);
            case NOT_EQ:
                return !left.equals(right);
            default:
                throw new PexException("Unhandled compare operator: " + this);
        }
    }

    private void checkIntOrDateValues(String operator, Object left, Object right) {
        if (left instanceof Long) {
            if (!(right instanceof Long)) {
                String fmt = "Cannot compare int value with right-hand-side value with runtime type " + right.getClass().getSimpleName();
                throw new PexRuntimeException(String.format(fmt, operator));
            }
            // else all good
        } else if (left instanceof LocalDate) {
            if (!(right instanceof LocalDate)) {
                String fmt = "Cannot compare date value with right-hand-side value with runtime type " + right.getClass().getSimpleName();
                throw new PexRuntimeException(String.format(fmt, operator));
            }
            // else all good
        } else {
            String fmt = "Compare operator '%s' only supports int and date values but left-hand-side value is not of these types";
            throw new PexRuntimeException(String.format(fmt, operator));
        }
    }
}
