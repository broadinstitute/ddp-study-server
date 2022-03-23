package org.broadinstitute.dsm.model.elastic.sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class ActivityTypeSort extends Sort {

    private List<Map<String, String>> possibleValues;
    private String originalOuterProperty;
    private String originalInnerProperty;

    ActivityTypeSort(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        super(sortBy, typeExtractor);
        this.originalOuterProperty = sortBy.getOuterProperty();
        this.originalInnerProperty = sortBy.getInnerProperty();
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        Optional<FieldSettingsDto> maybeFieldSettings =
                fieldSettingsDao.getFieldSettingsByFieldTypeAndColumnName(originalOuterProperty, originalInnerProperty);
        Optional<String> maybePossibleValues = maybeFieldSettings.map(FieldSettingsDto::getPossibleValues);
        if (maybePossibleValues.isPresent()) {
            String possibleValuesString = maybePossibleValues.get();
            this.possibleValues = ObjectMapperSingleton.readValue(possibleValuesString, new TypeReference<List<Map<String, String>>>() {
            });
        }
    }

    @Override
    public String handleInnerPropertySpecialCase() {
        return getFieldNameToSortBy(possibleValues);
    }

    private String getFieldNameToSortBy(List<Map<String, String>> possibleValues) {
        return possibleValues.stream().findFirst().map(mapValue -> mapValue.get(FieldSettings.KEY_VALUE))
                .map(value -> value.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR)[1]).orElse(StringUtils.EMPTY);
    }

    @Override
    String handleOuterPropertySpecialCase() {
        if (isQuestionsAnswers()) {
            return buildQuestionsAnswersPath();
        }
        return getAlias().getValue();
    }

    private boolean isQuestionsAnswers() {
        return Alias.REGISTRATION == getAlias();
    }

    @Override
    String buildNestedPath() {
        if (isQuestionsAnswers()) {
            return buildQuestionsAnswersPath();
        }
        return super.buildNestedPath();
    }

    @Override
    public Alias getAlias() {
        return Alias.valueOf(getOuterPropertyFromPossibleValues().toUpperCase());
    }

    private String getOuterPropertyFromPossibleValues() {
        return possibleValues.stream().findFirst().map(mapValue -> mapValue.get(FieldSettings.KEY_VALUE))
                .map(value -> value.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR)[0]).orElse(StringUtils.EMPTY);
    }

    @Override
    public String getRawAlias() {
        return getAlias().toString();
    }
}
