package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

@Value
@AllArgsConstructor
public class ActivityVersionDto {
    @ColumnName("activity_version_id")
    long id;

    @ColumnName("study_activity_id")
    long activityId;

    @ColumnName("version_tag")
    String versionTag;

    @ColumnName("revision_id")
    long revId;

    @ColumnName("start_date")
    long revStart;

    @ColumnName("end_date")
    Long revEnd;
}
