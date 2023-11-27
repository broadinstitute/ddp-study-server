package org.broadinstitute.dsm.route.admin;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.route.RouteUtil;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.AdminOperationService;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

/**
 * Provides an endpoint for admin operations
 */
public class AdminOperationRoute extends RequestHandler {
    private static final String OPERATION_ID = "operationId";
    private static final String OPERATION_TYPE_ID = "operationTypeId";

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {

        String realm = RouteUtil.requireParam(request, RoutePath.REALM);
        if (!UserUtil.checkUserAccess(realm, userId, UserAdminService.PEPPER_ADMIN_ROLE)) {
            throw new AuthorizationException();
        }
        AdminOperationService service = new AdminOperationService(RouteUtil.getUserEmail(userId), realm);

        String requestMethod = request.requestMethod();
        if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            String operationId = RouteUtil.requireParam(request, OPERATION_TYPE_ID);

            // give the handler all the query params as a map
            Map<String, String> attributes = request.queryMap().toMap().entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, fs -> fs.getValue()[0]));

            // if successful, returns an operation ID that can be used to get results
            return service.startOperation(operationId, attributes, request.body());

        } else if (requestMethod.equals(RoutePath.RequestMethod.GET.toString())) {
            QueryParamsMap queryParams = request.queryMap();
            String operationId = queryParams.value(OPERATION_ID);
            String operationTypeId = queryParams.value(OPERATION_TYPE_ID);

            if ((StringUtils.isBlank(operationId) && StringUtils.isBlank(operationTypeId))
                    || (!StringUtils.isBlank(operationId) && !StringUtils.isBlank(operationTypeId))) {
                throw new DSMBadRequestException(
                        String.format("Request requires %s or %s query parameter", OPERATION_ID, OPERATION_TYPE_ID));
            }

            if (!StringUtils.isBlank(operationId)) {
                return service.getOperationResults(operationId);
            }
            return service.getOperationTypeResults(operationTypeId);
        } else {
            throw new DsmInternalError("Invalid HTTP method for AdminOperationRoute: " + requestMethod);
        }
    }
}
