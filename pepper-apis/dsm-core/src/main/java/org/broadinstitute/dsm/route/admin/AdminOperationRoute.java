package org.broadinstitute.dsm.route.admin;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate;
import org.broadinstitute.dsm.route.RouteUtil;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.AdminOperationService;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

/**
 * Provides an endpoint for various admin operations
 */
public class AdminOperationRoute extends RequestHandler {
    private static final String OPERATION_ID = "operationId";

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {

        String realm = RouteUtil.requireRealm(request);
        if (!UserUtil.checkUserAccess(realm, userId, UserAdminService.PEPPER_ADMIN_ROLE)) {
            throw new AuthorizationException();
        }

        // give the handler all the query params as a map
        Map<String, String> attributes = request.queryMap().toMap().entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, fs -> fs.getValue()[0]));

        String operationId = attributes.get(OPERATION_ID);
        if (StringUtils.isBlank(operationId)) {
            throw new DSMBadRequestException("Operation ID cannot be empty");
        }

        AdminOperationService service = new AdminOperationService(userId);
        return service.startOperation(operationId, attributes, request.body());
    }
}
