package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExcludedParticipantField {
    private long id;
    private long configuredExportId;
    private String excludedParticipantField;

    @JdbiConstructor
    public ExcludedParticipantField(@ColumnName("excluded_participant_field_id") long id,
                                    @ColumnName("configured_export_id") long configuredExportId,
                                    @ColumnName("excluded_participant_field") String excludedParticipantField) {
        this.id = id;
        this.configuredExportId = configuredExportId;
        this.excludedParticipantField = excludedParticipantField;
    }

    public ExcludedParticipantField(long configuredExportId, String excludedParticipantField) {
        this.configuredExportId = configuredExportId;
        this.excludedParticipantField = excludedParticipantField;
    }

    public ExcludedParticipantField(String excludedParticipantField) {
        this.excludedParticipantField = excludedParticipantField;
    }

    public long getId() {
        return id;
    }

    public long getConfiguredExportId() {
        return configuredExportId;
    }

    public void setConfiguredExportId(long configuredExportId) {
        this.configuredExportId = configuredExportId;
    }

    public String getExcludedParticipantField() {
        return excludedParticipantField;
    }
}
