package org.broadinstitute.dsm.db.dao.kit;

import lombok.Getter;

@Getter
public enum KitCurrentStatus {
    QUEUE("Queue"), ERROR("Error"), RECEIVED("Received"), SENT("Sent"), KIT_WITHOUT_LABEL("Kit Without Label"), DEACTIVATED("Deactivated");
    private final String value;

    KitCurrentStatus(String name) {
        this.value = name;
    }
}