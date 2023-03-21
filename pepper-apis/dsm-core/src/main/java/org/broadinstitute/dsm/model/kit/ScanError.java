package org.broadinstitute.dsm.model.kit;

import lombok.Data;

@Data
public class ScanError {
    private String kit;
    private String error;
    private String shortId;

    public ScanError(String kit, String error) {
        this.kit = kit;
        this.error = error;
    }

    public ScanError(String kit, String error, String shortId) {
        this.kit = kit;
        this.error = error;
        this.shortId = shortId;
    }
}
