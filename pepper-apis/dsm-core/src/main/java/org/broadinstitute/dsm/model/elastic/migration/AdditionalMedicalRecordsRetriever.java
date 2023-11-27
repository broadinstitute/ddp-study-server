package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.Optional;

import org.broadinstitute.dsm.db.MedicalRecord;


interface AdditionalMedicalRecordsRetriever extends AdditionalRecordsRetriever<MedicalRecord> {

    static Optional<AdditionalMedicalRecordsRetriever> fromRealm(String realm) {
        return OLD_OSTEO_INSTANCE_NAME.equals(realm)
                ? Optional.of(new NewOsteoMedicalRecordsRetriever())
                : Optional.empty();
    }

}
