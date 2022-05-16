package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDao;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;

import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.DSM_OBJECT;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROPERTIES;

public class MedicalRecordFinalMappingMigrator extends DynamicFieldsMappingMigrator {

    protected MedicalRecordAbstractionFieldTypeParser parser;
    protected MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao = MedicalRecordAbstractionFieldDaoImpl.make();

    public MedicalRecordFinalMappingMigrator(String index, String study) {
        super(index, study);
        this.parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
    }

    public void setMedicalRecordAbstractionFieldDao(MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao) {
        this.medicalRecordAbstractionFieldDao = medicalRecordAbstractionFieldDao;
    }

    @Override
    protected void processAndBuildMapping() {
        var medicalRecordAbstractionFields = medicalRecordAbstractionFieldDao.getMedicalRecordAbstractionFieldsByInstanceName(study);
        var fieldTypeMappings = new HashMap<>();
        var dynamicFields = new HashMap<>(Map.of(DYNAMIC_FIELDS_WRAPPER_NAME, new HashMap<>(
                Map.of(PROPERTIES, fieldTypeMappings))));
        for (var field : medicalRecordAbstractionFields) {
            String displayName = field.getDisplayName();
            Integer orderNumber = field.getOrderNumber();
            String columnName = Util.underscoresToCamelCase(createColumnNameByDisplayNameAndOrderNumber(displayName, orderNumber));
            String fieldType = field.getType();
            String possibleValues = field.getPossibleValues();
            parser.setType(fieldType);
            parser.setPossibleValues(possibleValues);
            Object fieldTypeMapping = parser.parse(columnName); // date1 is mapping
            if (!fieldTypeMappings.containsKey(columnName)) {
                fieldTypeMappings.put(columnName, fieldTypeMapping);
            }
        }
        propertyMap.put("medicalRecordFinal", new HashMap<>(Map.of(PROPERTIES, dynamicFields)));
    }

    @Override
    protected Map<String, Object> buildFinalMapping() {
        var dsmLevelProperties = new HashMap<String, Object>(Map.of(PROPERTIES, propertyMap));
        var dsmLevel = new HashMap<String, Object>(Map.of(DSM_OBJECT, dsmLevelProperties));
        return new HashMap<>(Map.of(PROPERTIES, dsmLevel));

    }

    private String createColumnNameByDisplayNameAndOrderNumber(String displayName, Integer orderNumber) {
        String columnName = displayName;
        if (displayName != null && orderNumber != null) {
            columnName = displayName + orderNumber;
        }
        return columnName;
    }
}
