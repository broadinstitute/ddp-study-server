package org.broadinstitute.dsm.model.excel;

import java.util.Collection;
import java.util.List;

public class ParticipantRecord {
    private List<Object> values;
    private final int maxRows;
    public ParticipantRecord(List<Object> values) {
        this.values = values;
        this.maxRows = computeMaxRows();
    }

    private int computeMaxRows() {
        int maxRows = 1;
        for (Object value : values) {
            if (value instanceof Collection) {
                maxRows = Math.max(((Collection<?>) value).size(), maxRows);
            }
        }
        return maxRows;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public List<Object> getValues() {
        return values;
    }
}
