package org.broadinstitute.ddp.customexport.db.dto;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CustomExportDto {

    private final long umbrellaStudyId;
    private final Instant customExportLastCompleted;

    @JdbiConstructor
    public CustomExportDto(
            @ColumnName("study_id") long umbrellaStudyId,
            @ColumnName("customExportLastCompleted") Instant customExportLastCompleted) {
        this.umbrellaStudyId = umbrellaStudyId;
        this.customExportLastCompleted = customExportLastCompleted;
    }

    public long getUmbrellaStudyId() {
        return umbrellaStudyId;
    }

    public Instant getCustomExportLastCompleted() {
        return customExportLastCompleted;
    }
}
