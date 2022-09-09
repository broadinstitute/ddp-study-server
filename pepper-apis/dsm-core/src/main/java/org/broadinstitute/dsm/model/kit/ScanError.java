package org.broadinstitute.dsm.model.kit;

import lombok.Data;

@Data
public class ScanError {
    private String kit;
    private String error;

    public ScanError(String kit, String error) {
        this.kit = kit;
        this.error = error;
    }
}
