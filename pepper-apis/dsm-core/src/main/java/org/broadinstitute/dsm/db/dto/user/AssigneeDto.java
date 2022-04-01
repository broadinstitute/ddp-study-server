package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

public class AssigneeDto {
    private final long assigneeId;
    private final String name;
    private final String email;

    public AssigneeDto(long assigneeId, String name, String email) {
        this.assigneeId = assigneeId;
        this.name = name;
        this.email = email;
    }

    public long getAssigneeId() {
        return assigneeId;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }


}
