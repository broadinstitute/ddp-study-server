package org.broadinstitute.ddp.model.activity.instance.tabular;

import lombok.Value;
import org.broadinstitute.ddp.model.activity.instance.question.Question;

import java.util.ArrayList;
import java.util.List;

@Value
public class TabularContent {
    List<Question> questions = new ArrayList<>();
}
