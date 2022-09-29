package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoLive;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.MedicalRecordAbstractionDateTransformer;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.MedicalRecordAbstractionTextTransformer;

/**
 * Factory class for producing the instance which is responsible for building a map representation for ElasticSearch source map.
 */
public class MedicalRecordAbstractionValueTransformerFactory {

    /**
     * Static factory method for creating a concrete instance of MedicalRecordAbstractionTransformer
     * @param fieldType a concrete field type of medical record final data
     */
    public static MedicalRecordAbstractionTransformer getInstance(MedicalRecordAbstractionFieldType fieldType) {
        MedicalRecordAbstractionTransformer transformer;
        switch (fieldType) {
            case DATE:
                transformer = new MedicalRecordAbstractionDateTransformer();
                break;
            case NUMBER:
                transformer = new MedicalRecordAbstractionNumberTransformer();
                break;
            case BUTTON_SELECT:
                transformer = new MedicalRecordAbstractionButtonSelectTransformer();
                break;
            case MULTI_OPTIONS:
                transformer = new MedicalRecordAbstractionMultiOptionsTransformer();
                break;
            case TABLE:
            case MULTI_TYPE_ARRAY:
                transformer = new MedicalRecordAbstractionMultiTypeArrayTransformer(new MedicalRecordAbstractionFieldDaoLive());
                break;
            default:
                transformer = new MedicalRecordAbstractionTextTransformer();
        }
        return transformer;
    }

}
