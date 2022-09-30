
package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator.DSM_OBJECT;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.PROPERTIES;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.TYPE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDao;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoLive;
import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.model.elastic.export.parse.MedicalRecordAbstractionFieldTypeParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;

/**
 * A class which is responsible for migrating the mapping of `ddp_medical_record_final` objects into ElasticSearch.
 */
public class MedicalRecordFinalMappingMigrator extends DynamicFieldsMappingMigrator {

    public static final String MEDICAL_RECORD_FINAL = "medicalRecordFinal";

    protected MedicalRecordAbstractionFieldTypeParser parser;
    protected MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao;
    protected MedicalRecordFinalColumnNameBuilder columnNameBuilder;

    public MedicalRecordFinalMappingMigrator(String index, String study) {
        super(index, study);
        this.parser = new MedicalRecordAbstractionFieldTypeParser(new TypeParser());
        this.medicalRecordAbstractionFieldDao = new MedicalRecordAbstractionFieldDaoLive();
        this.columnNameBuilder = new MedicalRecordFinalColumnNameBuilderLive(CamelCaseConverter.of());
    }

    // for testing purposes
    void setMedicalRecordAbstractionFieldDao(
            MedicalRecordAbstractionFieldDao<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFieldDao) {
        this.medicalRecordAbstractionFieldDao = medicalRecordAbstractionFieldDao;
    }

    @Override
    protected void processAndBuildMapping() {
        List<MedicalRecordAbstractionFieldDto> medicalRecordAbstractionFields =
                medicalRecordAbstractionFieldDao.getMedicalRecordAbstractionFieldsByInstanceName(study);
        Map<String, Object> fieldTypeMappings = new HashMap<>();
        Map<String, Object> dynamicFields =
                new HashMap<>(Map.of(DYNAMIC_FIELDS_WRAPPER_NAME, new HashMap<>(Map.of(PROPERTIES, fieldTypeMappings))));
        for (MedicalRecordAbstractionFieldDto field : medicalRecordAbstractionFields) {
            String displayName    = field.getDisplayName();
            String columnName     = columnNameBuilder.apply(displayName);
            String fieldType      = field.getType();
            String possibleValues = field.getPossibleValues();
            parser.setType(fieldType);
            parser.setPossibleValues(possibleValues);
            Object fieldTypeMapping = parser.parse(columnName);
            if (!fieldTypeMappings.containsKey(columnName)) {
                fieldTypeMappings.put(columnName, fieldTypeMapping);
            }
        }
        propertyMap.put(MEDICAL_RECORD_FINAL, new HashMap<>(Map.of(TYPE, MappingGenerator.NESTED, PROPERTIES, dynamicFields)));
    }

    @Override
    protected Map<String, Object> buildFinalMapping() {
        Map<String, Object> dsmLevelProperties = new HashMap<>(Map.of(PROPERTIES, propertyMap));
        Map<String, Object> dsmLevel = new HashMap<>(Map.of(DSM_OBJECT, dsmLevelProperties));
        return new HashMap<>(Map.of(PROPERTIES, dsmLevel));
    }
}

