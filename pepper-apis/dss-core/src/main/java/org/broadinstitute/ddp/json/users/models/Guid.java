package org.broadinstitute.ddp.json.users.models;

import lombok.Value;

@Value
public class Guid {
    private String value;

    public String toString() {
        return value;
    }
}
