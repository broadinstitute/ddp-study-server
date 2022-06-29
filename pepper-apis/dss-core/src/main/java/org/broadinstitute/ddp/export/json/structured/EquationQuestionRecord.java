package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class EquationQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private final List<BigDecimal> answer = new ArrayList<>();

    public EquationQuestionRecord(String stableId, EquationResponse response) {
        super(QuestionType.EQUATION, stableId);
        if (response != null) {
            StreamEx.of(response.getValues()).map(DecimalDef::toBigDecimal).forEach(answer::add);
        }
    }

}
