package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
public class MedicalRecordMigrator extends BaseCollectionMigrator {

    public MedicalRecordMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.MEDICAL_RECORD);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<MedicalRecord>> medicalRecords = MedicalRecord.getMedicalRecords(realm);
        int recordsFromRealm = medicalRecords.size();
        AdditionalMedicalRecordsRetriever.fromRealm(realm)
                .ifPresent(retriever -> retriever.mergeRecords(medicalRecords));
        log.info("Migrator retrieved {} medical records from realm {}, and {} additional records",
                recordsFromRealm, realm, medicalRecords.size() - recordsFromRealm);
        return (Map) medicalRecords;
    }
}
