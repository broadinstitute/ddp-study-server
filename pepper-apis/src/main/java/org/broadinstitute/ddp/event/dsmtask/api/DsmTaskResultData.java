package org.broadinstitute.ddp.event.dsmtask.api;

import com.google.gson.annotations.SerializedName;

public class DsmTaskResultData {

    public static final String ATTR_TASK_MESSAGE_ID = "taskMessageId";

    public enum DsmTaskResultType {
        SUCCESS,
        ERROR
    }

    @SerializedName("resultType")
    private final DsmTaskResultType resultType;
    @SerializedName("errorMessage")
    private final String errorMessage;

    private final DsmTaskData dsmTaskData;


    public DsmTaskResultData(DsmTaskResultType resultType, DsmTaskData dsmTaskData) {
        this(resultType, null, dsmTaskData);
    }

    public DsmTaskResultData(DsmTaskResultType resultType, String errorMessage, DsmTaskData dsmTaskData) {
        this.resultType = resultType;
        this.errorMessage = errorMessage;
        this.dsmTaskData = dsmTaskData;
    }

    public DsmTaskResultType getResultType() {
        return resultType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public DsmTaskData getDsmTaskData() {
        return dsmTaskData;
    }

    public String toString() {
        return "resultType=" + resultType + "; errorMessage=" + errorMessage;
    }
}
