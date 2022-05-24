package org.broadinstitute.dsm.pubsub.study.osteo;

import java.util.function.Supplier;

public class DataCopyingException extends RuntimeException {

    private DataCopyingException(String columnName) {
        super(String.format("%s was null while copying data", columnName));
    }

    public static Supplier<DataCopyingException> withMessage(String columnName) {
        return () -> new DataCopyingException(columnName);
    }

}
