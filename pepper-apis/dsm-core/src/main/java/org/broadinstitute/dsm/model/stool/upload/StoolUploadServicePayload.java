package org.broadinstitute.dsm.model.stool.upload;

import lombok.Data;
import spark.Response;

@Data
public class StoolUploadServicePayload {
    private final String requestBody;
    private final Response response;
}
