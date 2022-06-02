package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.MedicalRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.OLD_OSTEO_INSTANCE_NAME;

interface AdditionalRecordsRetriever {

    static Optional<AdditionalRecordsRetriever> fromRealm(String realm) {
        return OLD_OSTEO_INSTANCE_NAME.equals(realm)
                ? Optional.of(new NewOsteoMedicalRecordsRetriever())
                : Optional.empty();
    }

    Map<String, List<MedicalRecord>> retrieve();

}