package org.broadinstitute.ddp.db.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public final class Limitation {
    Integer from;
    Integer to;

    public Integer getCount() {
        return Optional.ofNullable(to).orElse(50) - Optional.ofNullable(from).orElse(0);
    }
}
