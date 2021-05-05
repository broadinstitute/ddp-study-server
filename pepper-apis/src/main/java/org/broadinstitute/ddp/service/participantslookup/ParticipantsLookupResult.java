package org.broadinstitute.ddp.service.participantslookup;

import java.util.ArrayList;
import java.util.List;

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
