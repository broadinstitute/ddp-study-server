package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Data
@NoArgsConstructor
/**
 * List of requests to resample kits for participants with legacy short IDs
 */
public class LegacyKitResampleList {
    List<LegacyKitResampleRequest> resampleRequestList;

    @JsonCreator
    public LegacyKitResampleList(@JsonProperty("resampleRequestList") List<LegacyKitResampleRequest> resampleRequestList) {
        this.resampleRequestList = resampleRequestList;
    }

    public static LegacyKitResampleList fromJson(String payload) {
        LegacyKitResampleList req;
        try {
            req = ObjectMapperSingleton.instance().readValue(payload, LegacyKitResampleList.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid resample list request format. Payload: " + payload, e);
        }
        if (req.resampleRequestList == null || req.resampleRequestList.isEmpty()) {
            throw new DSMBadRequestException("Invalid resample list request. Empty resample list");
        }
        return req;
    }
}
