package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class FileAnswer extends Answer<FileInfo> {

    @SerializedName("value")
    private FileInfo value;

    public FileAnswer(Long answerId, String questionStableId, String answerGuid, FileInfo value, String instanceGuid) {
        super(QuestionType.FILE, answerId, questionStableId, answerGuid, instanceGuid);
        this.value = value;
    }

    public FileAnswer(Long answerId, String questionStableId, String answerGuid, FileInfo value) {
        super(QuestionType.FILE, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    @Override
    public FileInfo getValue() {
        return value;
    }

    @Override
    public void setValue(FileInfo value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }
}
