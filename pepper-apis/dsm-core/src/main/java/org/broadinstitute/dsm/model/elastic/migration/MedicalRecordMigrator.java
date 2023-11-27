package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class MedicalRecordMigrator extends BaseCollectionMigrator {

    public MedicalRecordMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.MEDICAL_RECORD);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<MedicalRecord>> medicalRecords = MedicalRecord.getMedicalRecords(realm);
        updateMedicalRecordsIfRequired(medicalRecords);
        return (Map) medicalRecords;
    }

    private void updateMedicalRecordsIfRequired(Map<String, List<MedicalRecord>> medicalRecords) {
        AdditionalMedicalRecordsRetriever.fromRealm(realm).ifPresent(retriever -> concatenateMedicalRecords(medicalRecords, retriever));
    }

    private void concatenateMedicalRecords(Map<String, List<MedicalRecord>> medicalRecords, AdditionalMedicalRecordsRetriever retriever) {
        Map<String, List<MedicalRecord>> additionalMedicalRecords = retriever.retrieve();
        additionalMedicalRecords.forEach((guid, records) -> {
            if (medicalRecords.containsKey(guid)) {
                List<MedicalRecord> mergedMedicalRecords =
                        Stream.concat(medicalRecords.get(guid).stream(), records.stream()).collect(Collectors.toList());
                medicalRecords.put(guid, mergedMedicalRecords);
            } else {
                medicalRecords.put(guid, records);
            }
        });
    }

}
