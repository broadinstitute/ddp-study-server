package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoImpl;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;

import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.DSM_OBJECT;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROPERTIES;

public class MedicalRecordFinalMappingMigrator extends DynamicFieldsMappingMigrator {

    protected MedicalRecordAbstractionFieldTypeParser parser;

    public MedicalRecordFinalMappingMigrator(String index, String study) {
        super(index, study);
        this.parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
    }

    @Override
    protected void processAndBuildMapping() {
        var medicalRecordAbstractionFieldDao = MedicalRecordAbstractionFieldDaoImpl.make();
        var medicalRecordAbstractionFields = medicalRecordAbstractionFieldDao.getMedicalRecordAbstractionFieldsByInstanceName(study);
        var fieldTypeMappings = new HashMap<>();
        var dynamicFields = new HashMap<>(Map.of(DYNAMIC_FIELDS_WRAPPER_NAME, new HashMap<>(
                Map.of(PROPERTIES, fieldTypeMappings))));
        for (var field : medicalRecordAbstractionFields) {
            String displayName = field.getDisplayName();
            Integer orderNumber = field.getOrderNumber();
            String columnName = Util.underscoresToCamelCase(createColumnNameByDisplayNameAndOrderNumber(displayName, orderNumber));
            String fieldType = field.getType();
            parser.setType(fieldType);
            Object fieldTypeMapping = parser.parse(columnName); // date1 is mapping
            if (!fieldTypeMappings.containsKey(columnName)) {
                fieldTypeMappings.put(columnName, fieldTypeMapping);
            }
        }
        propertyMap.put("medicalRecordFinal", new HashMap<>(Map.of(PROPERTIES, dynamicFields)));
    }

    @Override
    protected Map<String, Object> buildFinalMapping() {
        var dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, propertyMap));
        var dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        var finalMap = new HashMap<String, Object>(Map.of(PROPERTIES, dsmLevel));
        return finalMap;

    }

    private String createColumnNameByDisplayNameAndOrderNumber(String displayName, Integer orderNumber) {
        String columnName = displayName;
        if (displayName != null && orderNumber != null) {
            columnName = displayName + orderNumber;
        }
        return columnName;
    }
}
