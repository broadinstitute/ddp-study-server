package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.ElasticMappingExportAdapter;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.RequestPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParserFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.DSM_OBJECT;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROPERTIES;

public class DynamicFieldsMappingMigrator implements Exportable {

    public static final String DYNAMIC_FIELDS_WRAPPER_NAME = "dynamicFields";
    private final String index;
    private final String study;
    public DynamicFieldsParser parser;
    public Map<String, Object> propertyMap;

    private ElasticMappingExportAdapter elasticMappingExportAdapter;

    public DynamicFieldsMappingMigrator(String index, String study) {
        this.index = index;
        this.study = study;
        this.parser = new DynamicFieldsParser();
        this.parser.setParser(new TypeParser());
        this.propertyMap = new HashMap<>();
        elasticMappingExportAdapter = new ElasticMappingExportAdapter();
    }

    @Override
    public void export() {
        FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
        List<FieldSettingsDto> fieldSettingsByStudyName = fieldSettingsDao.getAllFieldSettings();
        for (FieldSettingsDto fieldSettingsDto : fieldSettingsByStudyName) {
            parser.setDisplayType(fieldSettingsDto.getDisplayType());
            parser.setPossibleValuesJson(fieldSettingsDto.getPossibleValues());
            String fieldType = fieldSettingsDto.getFieldType();
            BaseGenerator.PropertyInfo propertyInfo = Util.TABLE_ALIAS_MAPPINGS.get(fieldType);
            if (propertyInfo != null)
                buildMapping(fieldSettingsDto, propertyInfo);
            else
                buildMapping(fieldSettingsDto, new BaseGenerator.PropertyInfo(ParticipantData.class, true));
        }
        elasticMappingExportAdapter.setRequestPayload(new RequestPayload(index));
        elasticMappingExportAdapter.setSource(buildFinalMapping());
        elasticMappingExportAdapter.export();
    }

    private Map<String, Object> buildFinalMapping() {
        Map<String, Object> dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, propertyMap));
        Map<String, Map<String, Object>> dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        Map<String, Object> finalMap = new HashMap<>(Map.of(PROPERTIES, dsmLevel));
        return finalMap;
    }

    private void buildMapping(FieldSettingsDto fieldSettingsDto, BaseGenerator.PropertyInfo propertyInfo) {
        String columnName = Util.underscoresToCamelCase(fieldSettingsDto.getColumnName());
        String propertyName = propertyInfo.getPropertyName();
        Object typeMap = parser.parse(fieldSettingsDto.getDisplayType());
        if (!(propertyMap.containsKey(propertyName))) {
            Map<String, Object> dynamicFields = new HashMap<>(Map.of(DYNAMIC_FIELDS_WRAPPER_NAME, new HashMap<>(Map.of(PROPERTIES, new HashMap<>(Map.of(columnName, typeMap))))));
            Map<String, Object> wrapperMap = new HashMap<>();
            if (propertyInfo.isCollection()) {
                wrapperMap.put(MappingGenerator.TYPE, MappingGenerator.NESTED);
            }
            wrapperMap.put(PROPERTIES, dynamicFields);
            propertyMap.put(propertyName, wrapperMap);
        } else {
            Map<String, Object> outerMap = (Map<String, Object>) propertyMap.get(propertyName);
            Map<String, Object> outerProperties = (Map<String, Object>) outerMap.get(PROPERTIES);
            Map<String, Object> dynamicFieldsJson = (Map<String, Object>) outerProperties.get(DYNAMIC_FIELDS_WRAPPER_NAME);
            Map<String, Object> innerProperties = (Map<String, Object>) dynamicFieldsJson.get(PROPERTIES);
            innerProperties.putIfAbsent(columnName, typeMap);
        }
    }
}

