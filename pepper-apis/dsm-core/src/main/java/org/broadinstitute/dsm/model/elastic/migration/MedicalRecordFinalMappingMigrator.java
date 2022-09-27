
package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.DSM_OBJECT;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROPERTIES;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDao;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;
import org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;

public class MedicalRecordFinalMappingMigrator extends DynamicFieldsMappingMigrator {

    public static final String MEDICAL_RECORD_FINAL = "medicalRecordFinal";

    protected MedicalRecordAbstractionFieldTypeParser parser;
    protected MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao;
    protected CamelCaseConverter camelCaseConverter;

    public MedicalRecordFinalMappingMigrator(String index, String study) {
        super(index, study);
        this.parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
        this.medicalRecordAbstractionFieldDao = new MedicalRecordAbstractionFieldDaoImpl();
        this.camelCaseConverter = CamelCaseConverter.of();
        camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    // for testing purposes
    void setMedicalRecordAbstractionFieldDao(
            MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao) {
        this.medicalRecordAbstractionFieldDao = medicalRecordAbstractionFieldDao;
    }

    @Override
    protected void processAndBuildMapping() {
        // read abstraction fields by study
        List<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFields =
                medicalRecordAbstractionFieldDao.getMedicalRecordAbstractionFieldsByInstanceName(study);
        Map<String, Object> fieldTypeMappings = new HashMap<>();
        Map<String, Object> dynamicFields     = new HashMap<>(Map.of(DYNAMIC_FIELDS_WRAPPER_NAME,
                new HashMap<>(Map.of(PROPERTIES, fieldTypeMappings))));
        // loop for each, build mapping based on type and field name (ds name + ord_number)
        for (MedicalRecordAbstractionFieldDto field : medicalRecordAbstractionFields) {
            String displayName    = field.getDisplayName();
            int orderNumber       = field.getOrderNumber();
            String columnName     = createColumnNameByDisplayNameAndOrderNumber(displayName, orderNumber);
            String fieldType      = field.getType();
            String possibleValues = field.getPossibleValues();
            parser.setType(fieldType);
            parser.setPossibleValues(possibleValues);
            Object fieldTypeMapping = parser.parse(columnName);
            if (!fieldTypeMappings.containsKey(columnName)) {
                fieldTypeMappings.put(columnName, fieldTypeMapping);
            }
        }
        propertyMap.put(MEDICAL_RECORD_FINAL, new HashMap<>(Map.of(PROPERTIES, dynamicFields)));
    }

    @Override
    protected Map<String, Object> buildFinalMapping() {
        Map<String, Object> dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, propertyMap));
        Map<String, Object> dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        return new HashMap<>(Map.of(PROPERTIES, dsmLevel));

    }

    private String createColumnNameByDisplayNameAndOrderNumber(String displayName, Integer orderNumber) {
        String columnName = displayName;
        if (displayName != null && orderNumber != null) {
            columnName = displayName + orderNumber;
        }
        camelCaseConverter.setStringToConvert(columnName);
        return camelCaseConverter.convert();
    }
}

