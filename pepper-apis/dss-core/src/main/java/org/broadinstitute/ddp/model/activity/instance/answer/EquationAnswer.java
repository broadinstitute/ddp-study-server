package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import spark.utils.CollectionUtils;
import java.util.List;

@Getter
public final class EquationAnswer extends Answer<List<DecimalDef>> {
    @SerializedName("value")
    private final List<DecimalDef> values;

    public EquationAnswer(EquationResponse equation) {
        super(QuestionType.EQUATION, null, equation.getQuestionStableId(), null);
        this.values = equation.getValues();
    }

    @Override
    public List<DecimalDef> getValue() {
        return values;
    }

    @Override
    public void setValue(List<DecimalDef> value) {

    }

    @Override
    public boolean isEmpty() {
        return CollectionUtils.isEmpty(values);
    }
}

