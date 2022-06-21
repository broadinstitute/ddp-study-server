package org.broadinstitute.dsm.model.tags.cohort;

import java.util.concurrent.RecursiveAction;

class BulkCohortTagInsertAction extends RecursiveAction {

    static final int THRESHOLD = 500;
    int totalCount;
    int from;
    int to;

    public BulkCohortTagInsertAction(int totalCount, int from, int to) {
        this.totalCount = totalCount;
        this.from = from;
        this.to = to;
    }


    @Override
    protected void compute() {
        if (totalCount <= THRESHOLD) {
            System.out.println(from + " " + to);
            return;
        } else {
            int split = totalCount / 2;
            invokeAll(new BulkCohortTagInsertAction(split, split, split + THRESHOLD),
                    new BulkCohortTagInsertAction(totalCount - split, split + THRESHOLD, totalCount));
        }
    }
}
