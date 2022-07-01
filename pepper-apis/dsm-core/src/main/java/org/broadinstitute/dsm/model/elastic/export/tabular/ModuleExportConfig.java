package org.broadinstitute.dsm.model.elastic.export.tabular;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ModuleExportConfig {
    public ModuleExportConfig(ParticipantColumn participantColumn) {
        filterKey = Alias.of(participantColumn);
        if (filterKey.equals(Alias.ACTIVITIES)) {
            this.isActivity = true;
            this.name = participantColumn.getTableAlias();
        } else {
            this.name = StringUtils.isEmpty(filterKey.getValue()) ? filterKey.name() : filterKey.getValue();
        }
    }
    private String name;
    private Alias filterKey;
    private boolean isActivity = false;
    private List<FilterExportConfig> questions = new ArrayList<>();
    @Setter
    private int numMaxRepeats = 1;

    public boolean isCollection() {
        return filterKey.isCollection();
    }
    public String getAliasValue() {
        return filterKey.getValue();
    }

}
