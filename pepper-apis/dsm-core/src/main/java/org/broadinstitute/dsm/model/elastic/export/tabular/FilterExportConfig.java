package org.broadinstitute.dsm.model.elastic.export.tabular;

import lombok.Getter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.statics.DBConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class FilterExportConfig {
    public FilterExportConfig(ModuleExportConfig parent, Filter filterColumn, boolean splitOptionsIntoColumns, List<Map<String, Object>> options) {
        this.column = filterColumn.getParticipantColumn();
        this.type = filterColumn.getType();
        this.parent = parent;
        this.splitOptionsIntoColumns = splitOptionsIntoColumns;
        this.options = options;
    }
    private ParticipantColumn column;
    private ModuleExportConfig parent;
    private boolean splitOptionsIntoColumns = false;
    private String type;
    private List<Map<String, Object>> options = null;

}

