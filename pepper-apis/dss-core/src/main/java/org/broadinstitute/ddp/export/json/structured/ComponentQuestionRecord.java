package org.broadinstitute.ddp.export.json.structured;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

//class to represent Components like MailAddress, MedicalProviders as Composite QuestionAnswers
public final class ComponentQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<List<String>> answer = new ArrayList<>();

    public ComponentQuestionRecord(String stableId, List<List<String>> answer) {
        super(QuestionType.COMPOSITE, stableId);
        if (CollectionUtils.isNotEmpty(answer)) {
            this.answer = answer;
        }
    }
}
