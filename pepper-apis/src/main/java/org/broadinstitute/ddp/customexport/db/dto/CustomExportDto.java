package org.broadinstitute.ddp.customexport.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CustomExportDto {

    private final long id;
    private final long umbrellaStudyId;
    private final long customExportLastCompleted;

    @JdbiConstructor
    public CustomExportDto(
            @ColumnName("custom_export_id") long id,
            @ColumnName("umbrella_study_id") long umbrellaStudyId,
            @ColumnName("customExportLastCompleted") long customExportLastCompleted) {
        this.id = id;
        this.umbrellaStudyId = umbrellaStudyId;
        this.customExportLastCompleted = customExportLastCompleted;
    }

    public long getId() {
        return id;
    }

    public long getUmbrellaStudyId() {
        return umbrellaStudyId;
    }

    public long getCustomExportLastCompleted() {
        return customExportLastCompleted;
    }
}
