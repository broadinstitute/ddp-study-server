package org.broadinstitute.ddp.model.es;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Getter
@Setter
public class HiddenAlias {
    private String studyGuid;
    private String alias;
    private String stableId;

    @JdbiConstructor
    public HiddenAlias(@ColumnName("study_guid") String studyGuid,
                       @ColumnName("alias") String alias,
                       @ColumnName("stable_id") String stableId) {
        this.studyGuid = studyGuid;
        this.alias = alias;
        this.stableId = stableId;
    }
}
