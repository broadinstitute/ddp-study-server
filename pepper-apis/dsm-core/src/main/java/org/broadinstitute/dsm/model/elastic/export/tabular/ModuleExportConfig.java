package org.broadinstitute.dsm.model.elastic.export.tabular;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

@Getter
public class ModuleExportConfig {
    private final String name;
    private final String tableAlias;
    private final Alias filterKey;
    private final List<FilterExportConfig> questions = new ArrayList<>();
    private boolean isActivity = false;
    @Setter
    private int numMaxRepeats = 1;

    public ModuleExportConfig(ParticipantColumn participantColumn) {
        filterKey = Alias.of(participantColumn);
        tableAlias = participantColumn.getTableAlias();
        if (Alias.ACTIVITIES.equals(filterKey)) {
            this.isActivity = true;
            this.name = participantColumn.getTableAlias();
        } else {
            this.name = StringUtils.isEmpty(filterKey.getValue()) ? filterKey.name() : filterKey.getValue();
        }
    }

    public boolean isCollection() {
        return filterKey.isCollection();
    }

    public String getAliasValue() {
        return filterKey.getValue();
    }

}
