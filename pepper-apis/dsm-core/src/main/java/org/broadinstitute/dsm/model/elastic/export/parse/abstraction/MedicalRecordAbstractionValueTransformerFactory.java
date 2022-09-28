package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.MedicalRecordAbstractionDateTransformer;

public class MedicalRecordAbstractionValueTransformerFactory {

    public static MedicalRecordAbstractionTransformer getInstance(MedicalRecordAbstractionFieldType fieldType) {
        MedicalRecordAbstractionTransformer transformer;
        switch (fieldType) {
            case DATE:
                transformer = new MedicalRecordAbstractionDateTransformer();
                break;
            default:
                transformer = new MedicalRecordAbstractionDateTransformer();
        }
        return transformer;
    }

}
