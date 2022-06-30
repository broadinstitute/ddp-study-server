package org.broadinstitute.ddp.model.user;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum Role {
    Administrator("Administrator"),
    ELT("ELT"),
    DataManager("Data Manager"),
    Analyst("Analyst/Researcher");

    private final String name;
}
