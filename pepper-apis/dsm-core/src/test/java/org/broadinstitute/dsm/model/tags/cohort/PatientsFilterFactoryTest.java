package org.broadinstitute.dsm.model.tags.cohort;

import static org.junit.Assert.assertTrue;

import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.filter.participant.ManualFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.QuickFilterParticipantList;
import org.broadinstitute.dsm.model.filter.participant.SavedFilterParticipantList;
import org.junit.Test;

public class PatientsFilterFactoryTest {

    @Test
    public void instance() {
        BulkCohortTagPayload bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagPayload.setManualFilter("manualFilter");
        PatientsFilterFactory patientsFilterFactory = new PatientsFilterFactory(bulkCohortTagPayload);

        assertTrue(patientsFilterFactory.instance() instanceof ManualFilterParticipantList);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        ViewFilter viewFilter = new ViewFilter();
        viewFilter.setFilterName("filterName");
        bulkCohortTagPayload.setSavedFilter(viewFilter);
        patientsFilterFactory = new PatientsFilterFactory(bulkCohortTagPayload);

        assertTrue(patientsFilterFactory.instance() instanceof QuickFilterParticipantList);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        viewFilter = new ViewFilter();
        viewFilter.setFilters(new Filter[5]);
        bulkCohortTagPayload.setSavedFilter(viewFilter);
        patientsFilterFactory = new PatientsFilterFactory(bulkCohortTagPayload);

        assertTrue(patientsFilterFactory.instance() instanceof SavedFilterParticipantList);
    }
}
