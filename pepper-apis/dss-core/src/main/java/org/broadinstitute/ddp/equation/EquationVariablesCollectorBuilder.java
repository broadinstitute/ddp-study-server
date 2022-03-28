package org.broadinstitute.ddp.equation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationVariablesCollectorBuilder {
    public EquationVariablesCollector build() {
        return new EquationVariablesCollector(new HashSet<>());
    }
}
