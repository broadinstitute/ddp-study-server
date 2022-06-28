package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class EquationQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private List<BigDecimal> answer = new ArrayList<>();

    public EquationQuestionRecord(String stableId, EquationResponse response) {
        super(QuestionType.EQUATION, stableId);
        if (response != null && CollectionUtils.isNotEmpty(response.getValues())) {
            for (DecimalDef answerRow : response.getValues()) {
                this.answer.add(answerRow.toBigDecimal());
            }
        }
    }

    public List<BigDecimal> getAnswer() {
        return answer;
    }
}
