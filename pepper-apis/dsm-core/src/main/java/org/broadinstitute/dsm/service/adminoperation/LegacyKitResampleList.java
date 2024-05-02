package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import lombok.Data;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@Data
public class LegacyKitResampleList {
    List<LegacyKitResampleRequest> resampleRequestList;

    public static LegacyKitResampleList fromJson(String payload) {
        LegacyKitResampleList req;
        try {
            req = ObjectMapperSingleton.instance().readValue(payload, LegacyKitResampleList.class);
        } catch (Exception e) {
            throw new DSMBadRequestException("Invalid institution list request format. Payload: " + payload, e);
        }
        if (req.resampleRequestList == null || req.resampleRequestList.isEmpty()) {
            throw new DSMBadRequestException("Invalid institution list request. Empty institution list");
        }
        return req;
    }
}
