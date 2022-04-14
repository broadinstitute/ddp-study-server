package org.broadinstitute.ddp.customexport.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CompletedUserDto {

    private final long userId;
    private final long completedTime;

    @JdbiConstructor
    public CompletedUserDto(
            @ColumnName("user_id") long userId,
            @ColumnName("completed_time") long completedTime) {
        this.userId = userId;
        this.completedTime = completedTime;
    }

    public long getUserId() {
        return userId;
    }

    public long getCompletedTime() {
        return completedTime;
    }
}
