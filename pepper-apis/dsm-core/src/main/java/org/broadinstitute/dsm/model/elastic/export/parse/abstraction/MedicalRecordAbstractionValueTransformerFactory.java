package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.MedicalRecordAbstractionDateTransformer;
import org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.MedicalRecordAbstractionTextTransformer;

public class MedicalRecordAbstractionValueTransformerFactory {

    public static MedicalRecordAbstractionTransformer getInstance(MedicalRecordAbstractionFieldType fieldType) {
        MedicalRecordAbstractionTransformer transformer;
        switch (fieldType) {
            case DATE:
                transformer = new MedicalRecordAbstractionDateTransformer();
                break;
            case TEXT:
            case TEXT_AREA:
                transformer = new MedicalRecordAbstractionTextTransformer();
                break;
            default:
                transformer = null;
        }
        return transformer;
    }

}
