package org.broadinstitute.dsm.service.onchistory;

public class ColumnValidatorResponse {
    public boolean valid;
    public String errorMessage;
    public String newValue;

    public ColumnValidatorResponse() {
        valid = true;
    }
}
