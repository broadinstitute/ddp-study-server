package org.broadinstitute.ddp.model.activity.instance.answer;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.util.MiscUtil;

public class DateAnswer extends Answer<DateValue> {

    @Valid
    @NotNull
    @SerializedName("value")
    private DateValue value;

    public DateAnswer(Long answerId, String questionStableId, String answerGuid, DateValue value, Long languageCodeId) {
        super(QuestionType.DATE, answerId, questionStableId, answerGuid, languageCodeId);
        this.value = MiscUtil.checkNonNull(value, "value");
    }

    public DateAnswer(Long answerId, String questionStableId, String answerGuid,
                      Integer year, Integer month, Integer day, Long languageCodeId) {
        super(QuestionType.DATE, answerId, questionStableId, answerGuid, languageCodeId);
        this.value = new DateValue(year, month, day);
    }

    @Override
    public DateValue getValue() {
        return value;
    }

    @Override
    public void setValue(DateValue value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null || value.isBlank();
    }
}
