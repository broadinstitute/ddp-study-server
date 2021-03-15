package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class RgpExportDto {

    private long id;
    private long umbrellaStudyId;
    private long rgpExportLastCompleted;

    @JdbiConstructor
    public RgpExportDto(
            @ColumnName("rgp_export_id") long id,
            @ColumnName("umbrella_study_id") long umbrellaStudyId,
            @ColumnName("rgpExportLastCompleted") long rgpExportLastCompleted) {
        this.id = id;
        this.umbrellaStudyId = umbrellaStudyId;
        this.rgpExportLastCompleted = rgpExportLastCompleted;
    }

    public long getId() {
        return id;
    }

    public long getUmbrellaStudyId() {
        return umbrellaStudyId;
    }

    public long getRgpExportLastCompleted() {
        return rgpExportLastCompleted;
    }
}
