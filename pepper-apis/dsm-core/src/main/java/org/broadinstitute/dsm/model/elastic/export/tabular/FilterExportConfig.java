package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;

@Getter
public class FilterExportConfig {
    private final ParticipantColumn column;
    private final ModuleExportConfig parent;
    private final String type;
    private boolean splitOptionsIntoColumns = false;
    // whether this question has details associated with it -- this property is allowed to be dynamic since we don't know if a
    // question has details enabled until we start parsing participant responses
    @Setter
    private boolean hasDetails = false;
    private List<Map<String, Object>> options = null;
    private String collationSuffix = null;
    private int questionIndex = -1;

    public FilterExportConfig(ModuleExportConfig parent, Filter filterColumn, boolean splitOptionsIntoColumns,
                              List<Map<String, Object>> options, String collationSuffix, int questionIndex) {
        this.column = filterColumn.getParticipantColumn();
        this.type = filterColumn.getType();
        this.parent = parent;
        this.splitOptionsIntoColumns = splitOptionsIntoColumns;
        this.options = options;
        this.collationSuffix = collationSuffix;
        this.questionIndex = questionIndex;
    }
}

