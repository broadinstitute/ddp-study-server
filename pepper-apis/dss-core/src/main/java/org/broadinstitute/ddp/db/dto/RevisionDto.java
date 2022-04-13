package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class RevisionDto {
    long id;
    long startMillis;
    long changedUserId;
    String changedReason;
    Long endMillis;
    Long terminatedUserId;
    String terminatedReason;

    public static RevisionDto fromStartMetadata(long id, RevisionMetadata meta) {
        return new RevisionDto(id, meta.getTimestamp(), meta.getUserId(), meta.getReason(), null, null, null);
    }
}
