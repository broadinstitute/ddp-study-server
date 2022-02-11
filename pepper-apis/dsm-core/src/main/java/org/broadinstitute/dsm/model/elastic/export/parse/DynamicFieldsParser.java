package org.broadinstitute.dsm.model.elastic.export.parse;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.dsm.model.Filter.NUMBER;

public class DynamicFieldsParser extends BaseParser {

    public static final String DATE_TYPE = "DATE";
    public static final String CHECKBOX_TYPE = "CHECKBOX";
    public static final String ACTIVITY_STAFF_TYPE = "ACTIVITY_STAFF";
    public static final String ACTIVITY_TYPE = "ACTIVITY";
    protected String displayType;
    private String possibleValuesJson;
    private BaseParser parser;
    public FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();

    public void setDisplayType(String displayType) {
        this.displayType = displayType;
    }

    public void setPossibleValuesJson(String possibleValuesJson) {
        this.possibleValuesJson = possibleValuesJson;
    }

    public void setParser(BaseParser parser) {
        this.parser = parser;
    }

    public void setFieldSettingsDao(FieldSettingsDao fieldSettingsDao) {
        this.fieldSettingsDao = fieldSettingsDao;
    }



    @Override
    public Object parse(String element) {

        if (StringUtils.isBlank(displayType)) {
            getProperDisplayTypeWithPossibleValues();
        }

        Object parsedValue;
        if (DATE_TYPE.equals(displayType)) {
            parsedValue = forDate(element);
        } else if (CHECKBOX_TYPE.equals(displayType)) {
            parsedValue = forBoolean(element);
        } else if (isActivityRelatedType()) {
            Optional<String> maybeType = getTypeFromPossibleValuesJson();
            this.displayType = maybeType.orElse(StringUtils.EMPTY);
            parsedValue = maybeType
                    .map(displayType -> {
                        if (parser instanceof TypeParser) return parse(displayType);
                        else return parse(element);
                    })
                    .orElse(forString(element));
        } else if (NUMBER.equals(displayType)) {
            parsedValue = forNumeric(element);
        } else {
            parsedValue = forString(element);
        }

        displayType = null;

        return parsedValue;
    }

    protected void getProperDisplayTypeWithPossibleValues() {
        Optional<FieldSettingsDto> fieldSettingsByInstanceNameAndColumnName =
                fieldSettingsDao.getFieldSettingsByInstanceNameAndColumnName(realm, super.fieldName);
        if (fieldSettingsByInstanceNameAndColumnName.isPresent()) {
            FieldSettingsDto fieldSettings = fieldSettingsByInstanceNameAndColumnName.get();
            displayType = StringUtils.isNotBlank(fieldSettings.getDisplayType())
                    ? fieldSettings.getDisplayType()
                    : StringUtils.EMPTY;
            possibleValuesJson = StringUtils.isNotBlank(fieldSettings.getPossibleValues())
                    ? fieldSettings.getPossibleValues()
                    : StringUtils.EMPTY;
        } else {
            displayType = StringUtils.EMPTY;
        }
    }

    @Override
    protected Object forNumeric(String value) {
        return parser.forNumeric(value);
    }

    @Override
    protected Object forBoolean(String value) {
        return parser.forBoolean(value);
    }

    @Override
    protected Object forDate(String value) {
        return parser.forDate(value);
    }

    @Override
    protected Object forString(String value) {
        return parser.forString(value);
    }

    private Optional<String> getTypeFromPossibleValuesJson() {
        try {
            List<Map<String, String>> possibleValues = ObjectMapperSingleton.instance().readValue(possibleValuesJson, new TypeReference<List<Map<String, String>>>() {});
            Optional<String> maybeType = possibleValues.stream()
                    .filter(possibleValue -> possibleValue.containsKey(TYPE))
                    .map(possibleValue -> possibleValue.get(TYPE))
                    .findFirst();
            return maybeType;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean isActivityRelatedType() {
        return ACTIVITY_STAFF_TYPE.equals(displayType) || ACTIVITY_TYPE.equals(displayType);
    }
}
