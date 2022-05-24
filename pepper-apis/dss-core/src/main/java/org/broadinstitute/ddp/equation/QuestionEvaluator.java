package org.broadinstitute.ddp.equation;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.dao.QuestionCachedDao;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answerable;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
public final class QuestionEvaluator {
    private final Map<String, EquationResponse> values = new HashMap<>();
    private final Handle handle;
    private final String instanceGuid;

    public EquationResponse evaluate(final EquationQuestionDto equation) {
        final var variables = new EquationVariablesCollector(equation.getExpression()).collect();
        log.info("{} variables were found for equation {}", variables.size(), equation.getStableId());

        StreamEx.of(variables).remove(values::containsKey).forEach(this::fetchVariableValue);
        if (!StreamEx.of(variables).allMatch(values::containsKey)) {
            log.info("The equation {} can't be evaluated. Not all variables were populated", equation.getStableId());
            return null;
        }

        addValue(QuestionType.EQUATION, equation.getStableId(), EquationEvaluator.builder()
                .withVariablesValues(getVariablesValuesMap())
                .build()
                .evaluate(equation.getExpression())
                .toList());

        return values.get(equation.getStableId());
    }

    private Map<String, List<BigDecimal>> getVariablesValuesMap() {
        return StreamEx.of(values.values()).toMap(EquationResponse::getQuestionStableId, this::getAnswerValues);
    }

    private List<BigDecimal> getAnswerValues(final EquationResponse v) {
        return StreamEx.of(v.getValues()).map(this::toBigDecimal).toList();
    }

    private BigDecimal toBigDecimal(final DecimalDef value) {
        return Optional.ofNullable(value).map(DecimalDef::toBigDecimal).orElse(null);
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

        final var answers = questionCachedDao.getAnswerDao()
                .findAnswersByInstanceGuidAndQuestionStableId(instanceGuid, variable);

        if (answers.isEmpty()) {
            log.info("The answer doesn't exist for the question with stable id {}", variable);
            return;
        }

        addValue(question.get().getType(), variable, answers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addValue(final QuestionType type, final String variable, final List<?> values) {
        switch (type) {
            case NUMERIC:
                final var numericValues = (List<Answer>) values;
                this.values.put(variable, new EquationResponse(variable,
                        StreamEx.of(numericValues)
                                .map(Answerable::getValue)
                                .map(this::toLong)
                                .map(this::toDecimalDef)
                                .toList()));
                return;
            case PICKLIST:
                final var picklistAnswers = (List<PicklistAnswer>) values;
                this.values.put(variable, new EquationResponse(variable,
                        StreamEx.of(picklistAnswers)
                                .map(PicklistAnswer::getFirstPickedOption)
                                .map(SelectedPicklistOption::getValue)
                                .map(this::toDecimalDef)
                                .toList()));
                return;
            case EQUATION:
                final var equationValues = (List<BigDecimal>) values;
                this.values.put(variable, new EquationResponse(variable,
                        StreamEx.of(equationValues)
                                .map(this::toDecimalDef)
                                .toList()));
                return;
            case DECIMAL:
                final var decimalValues = (List<Answer>) values;
                this.values.put(variable, new EquationResponse(variable,
                        StreamEx.of(decimalValues)
                                .map(Answerable::getValue)
                                .map(this::toDecimalDef)
                                .toList()));
                return;
            default:
                log.warn("The question type {} is not supported by equations (variable: {})", type, variable);
        }
    }

    private Long toLong(final Object object) {
        return Optional.ofNullable(object).map(Long.class::cast).orElse(null);
    }

    private DecimalDef toDecimalDef(final Object object) {
        return Optional.ofNullable(object).map(DecimalDef.class::cast).orElse(null);
    }

    private DecimalDef toDecimalDef(final BigDecimal value) {
        return Optional.ofNullable(value).map(DecimalDef::new).orElse(null);
    }

    private DecimalDef toDecimalDef(final String value) {
        return Optional.ofNullable(value).map(DecimalDef::new).orElse(null);
    }

    private DecimalDef toDecimalDef(final Long value) {
        return Optional.ofNullable(value).map(DecimalDef::new).orElse(null);
    }
}
