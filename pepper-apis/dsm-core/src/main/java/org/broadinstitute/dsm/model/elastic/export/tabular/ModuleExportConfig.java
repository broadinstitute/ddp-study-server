package org.broadinstitute.dsm.model.elastic.export.tabular;

import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

import java.util.ArrayList;
import java.util.List;

public class ModuleExportConfig {
    public ModuleExportConfig(ParticipantColumn participantColumn) {
        Alias filterKey = Alias.of(participantColumn);
        if (filterKey.equals(Alias.ACTIVITIES)) {
            this.isActivity = true;
        }
        if (filterKey.isJson()) {
            this.isJson = true;
        }
        this.name = participantColumn.getTableAlias();
    }
    public String name;
    public boolean isActivity = false;
    public boolean isJson = false;
    public List<FilterExportConfig> questions = new ArrayList<>();
    public int numMaxRepeats = 1;
}
