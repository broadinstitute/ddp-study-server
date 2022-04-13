package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.util.List;

public final class FileAnswer extends Answer<List<FileInfo>> {

    @SerializedName("value")
    private List<FileInfo> value;

    public FileAnswer(Long answerId, String questionStableId, String answerGuid, List<FileInfo> value, String instanceGuid) {
        super(QuestionType.FILE, answerId, questionStableId, answerGuid, instanceGuid);
        this.value = value;
    }

    public FileAnswer(Long answerId, String questionStableId, String answerGuid, List<FileInfo> value) {
        super(QuestionType.FILE, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    @Override
    public List<FileInfo> getValue() {
        return value;
    }

    @Override
    public void setValue(List<FileInfo> value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    public FileInfo getInfo(int index) {
        if (value != null && value.size() > index) {
            return value.get(index);
        }
        throw new RuntimeException("File info with index " + index + " doesn't exist");
    }
}
