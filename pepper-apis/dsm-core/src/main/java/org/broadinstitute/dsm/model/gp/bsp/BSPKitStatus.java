package org.broadinstitute.dsm.model.gp.bsp;

import lombok.Getter;

@Getter
public class BSPKitStatus {

    public static final String EXITED = "EXITED";
    public static final String DEACTIVATED = "DEACTIVATED";

    public String status;

    public BSPKitStatus (String status) {
        this.status = status;
    }
}
