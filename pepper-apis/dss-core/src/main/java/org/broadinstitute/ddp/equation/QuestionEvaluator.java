package org.broadinstitute.ddp.equation;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.QuestionCachedDao;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class QuestionEvaluator {
    private final Map<String, EquationResponse> values = new HashMap<>();
    private final Handle handle;
    private final String instanceGuid;

    public EquationResponse evaluate(final EquationQuestionDto equation) {
        final var variables = new EquationVariablesCollector(equation.getExpression()).collect();

        StreamEx.of(variables).remove(values::containsKey).forEach(this::fetchVariableValue);
        if (!StreamEx.of(variables).allMatch(values::containsKey)) {
            log.info("The equation can't be evaluated. Not all variables were populated");
            return null;
        }

        addValue(QuestionType.EQUATION, equation.getStableId(), EquationEvaluator.builder()
                .withVariablesValues(getVariablesValuesMap())
                .build()
                .evaluate(equation.getExpression()));

        return values.get(equation.getStableId());
    }

    private Map<String, BigDecimal> getVariablesValuesMap() {
        return StreamEx.of(values.values()).toMap(EquationResponse::getQuestionStableId, this::getFirstAnswerValue);
    }

    private BigDecimal getFirstAnswerValue(final EquationResponse v) {
        return v.getValues().get(0).toBigDecimal();
    }

    private void fetchVariableValue(final String variable) {
        final var questionCachedDao = new QuestionCachedDao(handle);
        final var question = questionCachedDao.getJdbiQuestion().findDtoByStableIdAndInstanceGuid(variable, instanceGuid);
        if (question.isEmpty()) {
            log.error("The equation references to a question with stable id {} that doesn't exist", variable);
            return;
        }

        if (question.get().getType() == QuestionType.EQUATION) {
            evaluate((EquationQuestionDto) question.get());
            return;
        }

        final var answer = questionCachedDao.getAnswerDao()
                .findAnswerByInstanceGuidAndQuestionStableId(instanceGuid, variable);

        if (answer.isEmpty()) {
            log.info("The answer doesn't exist for the question with stable id {}", variable);
            return;
        }

        if (answer.get().getValue() == null) {
            log.info("The answer doesn't have the value for the question with stable id {}", variable);
            return;
        }

        addValue(question.get().getType(), variable, answer.get().getValue());
    }

    private void addValue(final QuestionType type, final String variable, final Object value) {
        switch (type) {
            case NUMERIC:
                values.put(variable, new EquationResponse(variable,
                        Collections.singletonList(new DecimalDef((Integer) value))));
                return;
            case PICKLIST:
                values.put(variable, new EquationResponse(variable,
                        Collections.singletonList(new DecimalDef((String) value))));
                return;
            case EQUATION:
                values.put(variable, new EquationResponse(variable,
                        Collections.singletonList(new DecimalDef((BigDecimal) value))));
                return;
            case DECIMAL:
                values.put(variable, new EquationResponse(variable,
                        Collections.singletonList((DecimalDef) value)));
                return;
            default:
                log.warn("The question type {} is not supported by equations", type);
        }
    }
}
