package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import java.util.Map;

public abstract class MedicalRecordAbstractionTransformer {

    public abstract Map<String, Object> toMap(String fieldName, String value);

}
