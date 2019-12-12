package org.broadinstitute.ddp.pex;

enum CompareOperator {
    LESS,
    LESS_EQ,
    GREATER,
    GREATER_EQ,
    EQ,
    NOT_EQ;

    boolean compare(long left, long right) {
        switch (this) {
            case LESS: return left < right;
            case LESS_EQ: return left <= right;
            case GREATER: return left > right;
            case GREATER_EQ: return left >= right;
            case EQ: return left == right;
            case NOT_EQ: return left != right;
            default:
                throw new PexException("Unknown compare operator: " + this);
        }
    }
}
