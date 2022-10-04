package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.source;

import org.broadinstitute.dsm.db.dao.ddp.abstraction.MedicalRecordAbstractionFieldDaoLive;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.MedicalRecordAbstractionFieldType;

/**
 * Factory class for producing the instance which is responsible for building a map representation for ElasticSearch source map.
 */
public class MedicalRecordAbstractionSourceGeneratorFactory {

    /**
     * Static factory method for creating a concrete instance of MedicalRecordAbstractionSourceGenerator
     * @param fieldType a concrete field type of medical record final data
     */
    public static MedicalRecordAbstractionSourceGenerator spawn(MedicalRecordAbstractionFieldType fieldType) {
        MedicalRecordAbstractionSourceGenerator generator;
        switch (fieldType) {
            case DATE:
                generator = new DateSourceGenerator();
                break;
            case NUMBER:
                generator = new NumericSourceGenerator();
                break;
            case BUTTON_SELECT:
                generator = new ButtonSelectSourceGenerator();
                break;
            case MULTI_OPTIONS:
                generator = new MultiOptionsSourceGenerator();
                break;
            case TABLE:
            case MULTI_TYPE_ARRAY:
                generator = new MultiTypeArraySourceGenerator(new MedicalRecordAbstractionFieldDaoLive());
                break;
            case OPTIONS:
                generator = new OptionsSourceGenerator();
                break;
            default:
                generator = new TextSourceGenerator();
        }
        return generator;
    }

}
