package org.broadinstitute.ddp.db.dto.kit;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class KitConfigurationDto {

    private long id;
    private long studyId;
    private String studyGuid;
    private long numberOfKits;
    private long kitTypeId;
    private boolean needsApproval;

    @JdbiConstructor
    public KitConfigurationDto(
            @ColumnName("kit_configuration_id") long id,
            @ColumnName("study_id") long studyId,
            @ColumnName("study_guid") String studyGuid,
            @ColumnName("number_of_kits") long numberOfKits,
            @ColumnName("kit_type_id") long kitTypeId,
            @ColumnName("needs_approval") boolean needsApproval) {
        this.id = id;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.numberOfKits = numberOfKits;
        this.kitTypeId = kitTypeId;
        this.needsApproval = needsApproval;
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public long getNumberOfKits() {
        return numberOfKits;
    }

    public long getKitTypeId() {
        return kitTypeId;
    }

    public boolean needsApproval() {
        return needsApproval;
    }
}
