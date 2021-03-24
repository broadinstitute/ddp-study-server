package org.broadinstitute.ddp.db;

/**
 * Used to store a value and an exception when performing DML.
 */
public class SimpleResult
{
    public Object resultValue;
    public Exception resultException;

    public SimpleResult()
    {
    }

    public SimpleResult(int resultValue)
    {
        this.resultValue = resultValue;
    }
}