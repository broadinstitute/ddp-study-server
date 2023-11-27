package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class UPSTrackingResponse {
    UPSTrackResponse trackResponse;
    UPSError[] errors;
}
