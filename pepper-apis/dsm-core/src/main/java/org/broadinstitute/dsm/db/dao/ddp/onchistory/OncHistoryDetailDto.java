package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import java.util.Map;

import lombok.Data;

@Data
public class OncHistoryDetailDto {
    private Map<String, Object> columnValues;

    public OncHistoryDetailDto(Map<String, Object> columnValues) {
        this.columnValues = columnValues;
    }
}
