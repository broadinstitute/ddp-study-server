package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;

import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.MedicalRecord;

class NewOsteoMedicalRecordsRetriever implements AdditionalMedicalRecordsRetriever {

    @Override
    public Map<String, List<MedicalRecord>> retrieve() {
        return MedicalRecord.getMedicalRecords(NEW_OSTEO_INSTANCE_NAME);
    }
}
