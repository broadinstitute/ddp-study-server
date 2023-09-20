package org.broadinstitute.dsm.service.admin;

import java.util.Map;

public interface AdminOperation {

    /**
     * Validate input and initialize operation, synchronously
     *
     * @param userId ID of user performing operation
     * @param realm study realm for operation (approved realm role for userId)
     * @param attributes key-values, if any
     * @param payload request, if any
     * @throws Exception for any errors
     */
    void initialize(String userId, String realm, Map<String, String> attributes, String payload);

    /**
     * Run operation, typically asynchronously
     *
     * @param operationId ID for reporting results
     */
    void run(int operationId);
}
