package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.source;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoLive;

/**
 * Factory class for producing the instance which is responsible for building a map representation for ElasticSearch source map.
 */
public class MedicalRecordAbstractionSourceGeneratorFactory {

    /**
     * Static factory method for creating a concrete instance of MedicalRecordAbstractionSourceGenerator
     * @param fieldType a concrete field type of medical record final data
     */
    public static MedicalRecordAbstractionSourceGenerator getInstance(MedicalRecordAbstractionFieldType fieldType) {
        MedicalRecordAbstractionSourceGenerator transformer;
        switch (fieldType) {
            case DATE:
                transformer = new DateSourceGenerator();
                break;
            case NUMBER:
                transformer = new NumericSourceGenerator();
                break;
            case BUTTON_SELECT:
                transformer = new ButtonSelectSourceGenerator();
                break;
            case MULTI_OPTIONS:
                transformer = new MultiOptionsSourceGenerator();
                break;
            case TABLE:
            case MULTI_TYPE_ARRAY:
                transformer = new MultiTypeArraySourceGenerator(new MedicalRecordAbstractionFieldDaoLive());
                break;
            case OPTIONS:
                transformer = new OptionsSourceGenerator();
                break;
            default:
                transformer = new TextSourceGenerator();
        }
        return transformer;
    }

}
