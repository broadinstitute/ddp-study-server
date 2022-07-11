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
        bulkCohortTagPayload.setSelectedOption(CreateOption.SELECTED_PATIENTS);
        BulkCohortTagCreationStrategyFactory bulkCohortTagCreationStrategyFactory =
                new BulkCohortTagCreationStrategyFactory(bulkCohortTagPayload);

        CohortStrategy strategy = bulkCohortTagCreationStrategyFactory.instance();
        assertTrue(strategy instanceof BaseCohortStrategy);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagPayload.setSavedFilter(new ViewFilter());
        bulkCohortTagPayload.setSelectedOption(CreateOption.ALL_PATIENTS);
        bulkCohortTagCreationStrategyFactory
                = new BulkCohortTagCreationStrategyFactory(bulkCohortTagPayload);
        assertTrue(bulkCohortTagCreationStrategyFactory.instance() instanceof FilteredOrAllPatientsCohortStrategy);

        bulkCohortTagPayload = new BulkCohortTagPayload();
        bulkCohortTagPayload.setSelectedOption(CreateOption.ALL_PATIENTS);
        bulkCohortTagCreationStrategyFactory
                = new BulkCohortTagCreationStrategyFactory(bulkCohortTagPayload);
        assertTrue(bulkCohortTagCreationStrategyFactory.instance() instanceof FilteredOrAllPatientsCohortStrategy);

    }


}
