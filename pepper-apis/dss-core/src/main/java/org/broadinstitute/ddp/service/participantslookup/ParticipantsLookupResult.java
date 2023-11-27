package org.broadinstitute.ddp.service.participantslookup;

import java.util.ArrayList;
import java.util.List;

/**
 * Object returned by {@link ParticipantsLookupService} and containing
 * results of participants lookup
 */
public class ParticipantsLookupResult<T> {

    private List<T> resultRows = new ArrayList<>();

    private int totalCount;

    public List<T> getResultRows() {
        return resultRows;
    }

    public void setResultRows(List<T> resultRows) {
        this.resultRows = resultRows;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
