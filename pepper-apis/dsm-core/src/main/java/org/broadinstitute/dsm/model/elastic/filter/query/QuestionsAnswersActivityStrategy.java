package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;

public class QuestionsAnswersActivityStrategy extends BaseActivityStrategy {
    protected QuestionsAnswersActivityStrategy(SplitterStrategy splitter, Operator operator) {
        super(splitter, operator);
    }
}
