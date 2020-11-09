package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.AgeRangeRuleDto;
import org.broadinstitute.ddp.db.dto.validation.DateRangeRuleDto;
import org.broadinstitute.ddp.db.dto.validation.IntRangeRuleDto;
import org.broadinstitute.ddp.db.dto.validation.LengthRuleDto;
import org.broadinstitute.ddp.db.dto.validation.NumOptionsSelectedRuleDto;
import org.broadinstitute.ddp.db.dto.validation.RegexRuleDto;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

@RegisterConstructorMapper(RuleDto.class)
@RegisterConstructorMapper(AgeRangeRuleDto.class)
@RegisterConstructorMapper(DateRangeRuleDto.class)
@RegisterConstructorMapper(IntRangeRuleDto.class)
@RegisterConstructorMapper(LengthRuleDto.class)
@RegisterConstructorMapper(NumOptionsSelectedRuleDto.class)
@RegisterConstructorMapper(RegexRuleDto.class)
public interface JdbiQuestionValidation extends SqlObject {

    @SqlUpdate("insert into question__validation(question_id,validation_id) values(:questionId,:validationId)")
    @GetGeneratedKeys
    long insert(long questionId, long validationId);

    @UseStringTemplateSqlLocator
    @SqlQuery("getAllActiveValidations")
    @UseRowReducer(RuleDtoReducer.class)
    List<RuleDto> getAllActiveValidations(@Bind("questionId") long questionId);

    default List<RuleDto> getAllActiveValidations(QuestionDto questionDto) {
        return getAllActiveValidations(questionDto.getId());
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("getAllActiveValidationDtosForActivity")
    @UseRowReducer(RuleDtoReducer.class)
    Stream<RuleDto> getAllActiveValidationDtosForActivity(@Bind("activityId") long activityId);

    default Map<Long, List<RuleDto>> getAllActiveValidationsForActivity(long activityId) {
        Map<Long, List<RuleDto>> questionIdToRuleDtos = new HashMap<>();
        try (var stream = getAllActiveValidationDtosForActivity(activityId)) {
            stream.forEach(ruleDto -> questionIdToRuleDtos
                    .computeIfAbsent(ruleDto.getQuestionId(), k -> new ArrayList<>())
                    .add(ruleDto));
        }
        return questionIdToRuleDtos;
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("getRequiredValidationIfActive")
    @UseRowReducer(RuleDtoReducer.class)
    Optional<RuleDto> getRequiredValidationIfActive(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findDtosByQuestionIdsAndTimestamp")
    @UseRowReducer(RuleDtoReducer.class)
    Stream<RuleDto> findDtosByQuestionIdsAndTimestamp(
            @BindList(value = "questionIds", onEmpty = EmptyHandling.NULL) Iterable<Long> questionIds,
            @Bind("timestamp") long timestamp);

    class RuleDtoReducer implements LinkedHashMapRowReducer<Long, RuleDto> {
        @Override
        public void accumulate(Map<Long, RuleDto> container, RowView view) {
            RuleDto ruleDto;
            var type = RuleType.valueOf(view.getColumn("rule_type", String.class));
            switch (type) {
                case AGE_RANGE:
                    ruleDto = view.getRow(AgeRangeRuleDto.class);
                    break;
                case DATE_RANGE:
                    ruleDto = view.getRow(DateRangeRuleDto.class);
                    break;
                case INT_RANGE:
                    ruleDto = view.getRow(IntRangeRuleDto.class);
                    break;
                case LENGTH:
                    ruleDto = view.getRow(LengthRuleDto.class);
                    break;
                case NUM_OPTIONS_SELECTED:
                    ruleDto = view.getRow(NumOptionsSelectedRuleDto.class);
                    break;
                case REGEX:
                    ruleDto = view.getRow(RegexRuleDto.class);
                    break;
                case DAY_REQUIRED:      // fall-through
                case MONTH_REQUIRED:    // fall-through
                case YEAR_REQUIRED:     // fall-through
                case COMPLETE:          // fall-through
                case REQUIRED:
                    ruleDto = view.getRow(RuleDto.class);
                    break;
                default:
                    throw new DaoException("Unknown validation rule type " + type);
            }
            container.put(ruleDto.getId(), ruleDto);
        }
    }
}
