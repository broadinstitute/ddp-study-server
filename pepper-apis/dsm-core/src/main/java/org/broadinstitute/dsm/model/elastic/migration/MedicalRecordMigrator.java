package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import java.util.Map;

public class MedicalRecordMigrator extends BaseCollectionMigrator {

    public MedicalRecordMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.MEDICAL_RECORD);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) MedicalRecord.getMedicalRecords(realm);
    }

}
