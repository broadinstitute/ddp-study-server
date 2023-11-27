package org.broadinstitute.ddp.audit;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.db.common.Limitation;
import org.broadinstitute.ddp.db.dto.AuditTrailDto;
import org.jdbi.v3.core.mapper.Nested;

import java.util.Optional;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public final class AuditTrailFilter {
    @Nested
    private AuditTrailDto constraints;

    @Nested
    private Limitation limitation;

    public Limitation getLimitation() {
        return Optional.ofNullable(limitation).orElse(new Limitation());
    }

    public AuditTrailDto getConstraints() {
        return Optional.ofNullable(constraints).orElse(AuditTrailDto.builder().build());
    }
}
