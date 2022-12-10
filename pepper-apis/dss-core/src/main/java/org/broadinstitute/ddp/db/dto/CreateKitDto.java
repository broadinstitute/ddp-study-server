package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class CreateKitDto extends QueuedEventDto {
    long createKitConfigurationId;

    @JdbiConstructor
    public CreateKitDto(
            @Nested QueuedEventDto pendingEvent,
            @ColumnName("create_kit_configuration_id") long createKitConfigurationId) {
        super(pendingEvent);
        this.createKitConfigurationId = createKitConfigurationId;
    }
}
