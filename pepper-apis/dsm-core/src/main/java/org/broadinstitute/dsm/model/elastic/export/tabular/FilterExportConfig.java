package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;

@Getter
public class FilterExportConfig {
    private final ParticipantColumn column;
    private final ModuleExportConfig parent;
    private final String type;
    private boolean splitOptionsIntoColumns = false;
    private List<Map<String, Object>> options = null;
    private String collationSuffix = null;

    public FilterExportConfig(ModuleExportConfig parent, Filter filterColumn, boolean splitOptionsIntoColumns,
                              List<Map<String, Object>> options, String collationSuffix) {
        this.column = filterColumn.getParticipantColumn();
        this.type = filterColumn.getType();
        this.parent = parent;
        this.splitOptionsIntoColumns = splitOptionsIntoColumns;
        this.options = options;
        this.collationSuffix = collationSuffix;
    }
}

