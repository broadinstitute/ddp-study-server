package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.transformers.DateTimeFormatUtils;

/**
 * Rule:
 * - if all date fields are defined and question is required (i.e. birthday questions),
 *   format it into a single column using American date format
 * - otherwise, one column for each defined date field,
 *   but if only one date field is defined then don't add field name to header
 * - date values are zero padded
 * - null values result in an empty cell
 */
public class DateQuestionFormatStrategy implements ResponseFormatStrategy<DateQuestionDef, DateAnswer> {

    @Override
    public Map<String, Object> mappings(DateQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();

        boolean isRequired = definition.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.REQUIRED);
        boolean hasCompleteRule = definition.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.COMPLETE);

        if (definition.getFields().size() == 3 && (isRequired || hasCompleteRule)) {
            String formats = MappingUtil.appendISODateFormat(DateTimeFormatUtils.DEFAULT_DATE_PATTERN);
            props.put(definition.getStableId(), MappingUtil.newDateType(formats, false));
        } else {
            boolean appendField = (definition.getFields().size() > 1);
            for (DateFieldType field : definition.getFields()) {
                String key = fieldHeader(definition.getStableId(), field, appendField);
                props.put(key, MappingUtil.newIntType());
            }
        }

        return props;
    }

    @Override
    public Map<String, Object> questionDef(DateQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().render("en")));

        if (definition.getFields().size() != 3) {
            List<String> dateElements = new ArrayList<>();

            for (DateFieldType field : definition.getFields()) {
                dateElements.add(field.name());
            }
            if (!dateElements.isEmpty()) {
                props.put("dateFields", dateElements);
            }
        }

        return props;
    }

    @Override
    public List<String> headers(DateQuestionDef definition) {
        boolean isRequired = definition.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.REQUIRED);
        boolean hasCompleteRule = definition.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.COMPLETE);

        if (definition.getFields().size() == 3 && (isRequired || hasCompleteRule)) {
            return Arrays.asList(definition.getStableId());
        } else {
            boolean appendField = (definition.getFields().size() > 1);
            return definition.getFields().stream()
                    .map(field -> fieldHeader(definition.getStableId(), field, appendField))
                    .collect(Collectors.toList());
        }
    }

    public List<String> headers(DateQuestionDef definition, int number) {
        return Arrays.asList(definition.getStableId() + "_" + number);
    }

    @Override
    public Map<String, String> collect(DateQuestionDef question, DateAnswer answer) {
        Map<String, String> record = new HashMap<>();
        DateValue ans = answer.getValue();
        if (ans == null) {
            return record;
        }

        boolean isRequired = question.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.REQUIRED);
        boolean hasCompleteRule = question.getValidations().stream()
                .anyMatch(rule -> rule.getRuleType() == RuleType.COMPLETE);

        if (question.getFields().size() == 3 && (isRequired || hasCompleteRule)) {
            record.put(question.getStableId(), ans.toDefaultDateFormat());
        } else {
            boolean appendField = (question.getFields().size() > 1);
            for (DateFieldType field : question.getFields()) {
                String key = fieldHeader(question.getStableId(), field, appendField);
                String value = null;
                if (field == DateFieldType.YEAR && ans.getYear() != null) {
                    value = String.format("%04d", ans.getYear());
                } else if (field == DateFieldType.MONTH && ans.getMonth() != null) {
                    value = String.format("%02d", ans.getMonth());
                } else if (field == DateFieldType.DAY && ans.getDay() != null) {
                    value = String.format("%02d", ans.getDay());
                }
                record.put(key, value);
            }
        }
        return record;
    }

    public Map<String, String> collect(DateQuestionDef question, DateAnswer answer, int i) {
        Map<String, String> record = new HashMap<>();
        DateValue ans = answer.getValue();
        if (ans == null) {
            return record;
        }
        record.put(question.getStableId() + "_" + i, ans.toDefaultDateFormat());
        return record;
    }

    private String fieldHeader(String questionStableId, DateFieldType field, boolean appendField) {
        return appendField ? questionStableId + "_" + field.name() : questionStableId;
    }
}
