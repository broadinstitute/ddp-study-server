package org.broadinstitute.dsm.service.adminoperation;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DSMBadRequestException;

public class LegacyKitResampleService extends ParticipantAdminOperationService {
    private DDPInstance ddpInstance;
    private KitRequestShipping kitRequestShipping;
    private LegacyKitResampleList legacyKitResampleList;

    /**
     * Validate input and initialize operation, synchronously
     *
     * @param userId ID of user performing operation
     * @param realm study realm for operation (approved realm role for userId)
     * @param attributes key-values, if any
     * @param payload request, if any
     * @throws Exception for any errors
     */
    @Override
    public void initialize(String userId, String realm, Map<String, String> attributes, String payload) {
        ddpInstance = DDPInstance.getDDPInstance(realm);

        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Missing required payload");
        }

        // get and verify content of payload
        legacyKitResampleList = LegacyKitResampleList.fromJson(payload);
        legacyKitResampleList.getResampleRequestList().forEach(legacyKitResampleRequest -> legacyKitResampleRequest.verify(ddpInstance));

    }

    /**
     * Run operation, typically asynchronously
     *
     * @param operationId ID for reporting results
     */
    @Override
    public void run(int operationId) {

    }
}
