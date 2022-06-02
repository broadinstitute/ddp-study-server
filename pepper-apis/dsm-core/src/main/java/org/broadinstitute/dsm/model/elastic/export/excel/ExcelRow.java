package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.List;

public class ExcelRow {
    private final List<String> rowData;

    public ExcelRow(List<String> rowData) {
        this.rowData = rowData;
    }

    public List<String> getRowData() {
        return rowData;
    }
}
