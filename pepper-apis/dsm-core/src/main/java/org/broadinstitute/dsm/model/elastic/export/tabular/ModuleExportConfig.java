package org.broadinstitute.dsm.model.elastic.export.tabular;

import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ModuleExportConfig {
    public ModuleExportConfig(ParticipantColumn participantColumn) {
        Alias filterKey = Alias.of(participantColumn);
        if (filterKey.equals(Alias.ACTIVITIES)) {
            this.isActivity = true;
            this.name = participantColumn.getTableAlias();
        } else {
            this.name = StringUtils.isEmpty(filterKey.getValue()) ? filterKey.name() : filterKey.getValue();
        }
        if (filterKey.isJson()) {
            this.isJson = true;
        }
    }
    public String name;
    public boolean isActivity = false;
    public boolean isJson = false;
    public List<FilterExportConfig> questions = new ArrayList<>();
    public int numMaxRepeats = 1;
}
