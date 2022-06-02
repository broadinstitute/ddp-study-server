package org.broadinstitute.dsm.model.elastic.migration;

import org.broadinstitute.dsm.db.MedicalRecord;

import java.util.List;
import java.util.Map;

import static org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter.NEW_OSTEO_INSTANCE_NAME;

class NewOsteoMedicalRecordsRetriever implements AdditionalRecordsRetriever {

    @Override
    public Map<String, List<MedicalRecord>> retrieve() {
        return MedicalRecord.getMedicalRecords(NEW_OSTEO_INSTANCE_NAME);
    }
}
