package org.broadinstitute.dsm.model.tags.cohort;

import static org.junit.Assert.*;

import org.junit.Test;

public class BulkCohortTagInsertActionTest {


    @Test
    public void compute() {
        BulkCohortTagInsertAction bulkCohortTagInsertAction = new BulkCohortTagInsertAction(2_000, 0, 500);
        bulkCohortTagInsertAction.compute();
    }



}