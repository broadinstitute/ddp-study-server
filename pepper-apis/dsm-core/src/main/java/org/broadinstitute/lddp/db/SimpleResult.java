package org.broadinstitute.lddp.db;

/**
 * Used to store a value and an exception when performing DML.
 */
public class SimpleResult {
    public Object resultValue;
    public Exception resultException;

    public SimpleResult() {
    }

    public SimpleResult(int resultValue) {
        this.resultValue = resultValue;
    }

    public SimpleResult(Object resultValue) {
        this.resultValue = resultValue;
    }
}
