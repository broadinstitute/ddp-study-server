package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.ESObjectConstants;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.OLD_OSTEO_INSTANCE_NAME;

public class MedicalRecordMigrator extends BaseCollectionMigrator {

    public MedicalRecordMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.MEDICAL_RECORD);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, List<MedicalRecord>> medicalRecords = MedicalRecord.getMedicalRecords(realm);
        AdditionalDataRetriever.fromRealm(realm).ifPresent(dataRetriever -> concatenateMedicalRecords(medicalRecords, dataRetriever));
        return (Map) medicalRecords;
    }

    private void concatenateMedicalRecords(Map<String, List<MedicalRecord>> medicalRecords, AdditionalDataRetriever dataRetriever) {
        Map<String, List<MedicalRecord>> additionalMedicalRecords = dataRetriever.retrieve();
        additionalMedicalRecords.forEach((guid, records) -> {
            if (medicalRecords.containsKey(guid)) {
                List<MedicalRecord> mergedMedicalRecords = Stream.concat(medicalRecords.get(guid).stream(), records.stream()).collect(Collectors.toList());
                medicalRecords.put(guid, mergedMedicalRecords);
            } else {
                medicalRecords.put(guid, records);
            }
        });
    }

}

interface AdditionalDataRetriever {

    static Optional<AdditionalDataRetriever> fromRealm(String realm) {
        return OLD_OSTEO_INSTANCE_NAME.equals(realm)
                ? Optional.of(new NewOsteoMedicalRecordsRetriever())
                : Optional.empty();
    }

    Map<String, List<MedicalRecord>> retrieve();

}

class NewOsteoMedicalRecordsRetriever implements AdditionalDataRetriever {

    @Override
    public Map<String, List<MedicalRecord>> retrieve() {
        return MedicalRecord.getMedicalRecords(NEW_OSTEO_INSTANCE_NAME);
    }
}
