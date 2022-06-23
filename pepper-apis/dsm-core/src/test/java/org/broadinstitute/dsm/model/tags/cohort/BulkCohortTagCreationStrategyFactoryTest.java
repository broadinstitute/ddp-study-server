package org.broadinstitute.dsm.model.tags.cohort;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dto.tag.cohort.BulkCohortTagPayload;
import org.junit.Test;

public class BulkCohortTagCreationStrategyFactoryTest {


    @Test
    public void instance() {
        BulkCohortTagPayload bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagPayload.setSelectedPatients(Arrays.asList("P1", "P2"));
        BulkCohortTagCreationStrategyFactory bulkCohortTagCreationStrategyFactory =
                new BulkCohortTagCreationStrategyFactory(null, null, bulkCohortTagPayload, null);

        CohortStrategy strategy = bulkCohortTagCreationStrategyFactory.instance();
        assertTrue(strategy instanceof SelectedPatientsCohortStrategy);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagPayload.setSavedFilter(new ViewFilter());
        bulkCohortTagCreationStrategyFactory
                = new BulkCohortTagCreationStrategyFactory(null, null, bulkCohortTagPayload, null);
        assertTrue(bulkCohortTagCreationStrategyFactory.instance() instanceof FilteredOrAllPatientsCohortStrategy);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagCreationStrategyFactory
                = new BulkCohortTagCreationStrategyFactory(null, null, bulkCohortTagPayload, null);
        assertTrue(bulkCohortTagCreationStrategyFactory.instance() instanceof FilteredOrAllPatientsCohortStrategy);

    }


}
