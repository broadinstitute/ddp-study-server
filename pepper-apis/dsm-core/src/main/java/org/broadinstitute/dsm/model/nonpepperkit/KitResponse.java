package org.broadinstitute.dsm.model.nonpepperkit;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class KitResponse {
    public String errorMessage;
    public String juniperKitId;
    public Object value;
}
