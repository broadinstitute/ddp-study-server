package org.broadinstitute.ddp.service.participantslookup;

import java.util.List;

public class ParticipantsLookupResult<T> {

    private List<T> resultRows;

    private long totalCount;

    public List<T> getResultRows() {
        return resultRows;
    }

    public void setResultRows(List<T> resultRows) {
        this.resultRows = resultRows;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }
}
