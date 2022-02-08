package org.broadinstitute.ddp.pex;

enum UnaryOperator {
    NOT, NEG;

    Object apply(Object value) {
        if (this == NOT) {
            if (!(value instanceof Boolean)) {
                throw new PexRuntimeException("Cannot apply logical NOT to value with runtime type " + value.getClass().getSimpleName());
            } else {
                return !((Boolean) value);
            }
        } else {
            if (!(value instanceof Long)) {
                throw new PexRuntimeException("Cannot apply negation to value with runtime type " + value.getClass().getSimpleName());
            } else {
                return -((Long) value);
            }
        }
    }
}
