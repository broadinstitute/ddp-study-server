package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import java.util.Collection;

public class ViewFilterRoute extends RequestHandler {

    private PatchUtil patchUtil;

    public ViewFilterRoute(@NonNull PatchUtil patchUtil) {
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (patchUtil.getColumnNameMap() == null) {
            throw new RuntimeException("ColumnNameMap is null!");
        }
        QueryParamsMap queryParams = request.queryMap();
        String parent = null;
        if (queryParams.value(DBConstants.FILTER_PARENT) != null) {
            parent = queryParams.get(DBConstants.FILTER_PARENT).value();
        }
        String realm = null;
        DDPInstance instance = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
            instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.HAS_MEDICAL_RECORD_INFORMATION_IN_DB);
        }
        else {
            throw new RuntimeException("No realm is sent!");
        }
        if (UserUtil.checkUserAccess(realm, userId, "mr_view") || UserUtil.checkUserAccess(realm, userId, "pt_list_view")) {
            String json = request.body();
            String userIdRequest = null;
            if (queryParams.value(UserUtil.USER_ID) != null) {
                userIdRequest = queryParams.get(UserUtil.USER_ID).value();
                if (!userId.equals(userIdRequest)) {
                    throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                }
            }

            if (request.url().contains(RoutePath.GET_FILTERS)) {
                if (StringUtils.isNotBlank(realm)) {
                    String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                    return ViewFilter.getAllFilters(userIdRequest, patchUtil.getColumnNameMap(), parent, ddpGroupId, instance.getDdpInstanceId());
                }
                else {
                    if (!request.url().contains(RoutePath.GET_DEFAULT_FILTERS)) {
                        Collection<String> realms = UserUtil.getListOfAllowedRealms(userIdRequest);
                        realm = realms.iterator().next();
                        String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                        return ViewFilter.getAllFilters(userIdRequest, patchUtil.getColumnNameMap(), parent, ddpGroupId, instance.getDdpInstanceId());
                    }
                }
            }
            else if (request.url().contains(RoutePath.SAVE_FILTER)) {
                String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                return ViewFilter.saveFilter(json, userIdRequest, patchUtil.getColumnNameMap(), ddpGroupId);
            }
            else if (request.url().contains(RoutePath.FILTER_DEFAULT)) {
                if (StringUtils.isBlank(parent)) {
                    throw new RuntimeException("No parent was sent in the request.");
                }
                String userMail = queryParams.get(RequestParameter.USER_MAIL).value();
                String filterName = queryParams.get(RequestParameter.FILTER_NAME).value();
                return ViewFilter.setDefaultFilter(filterName, userMail, parent);
            }
            throw new RuntimeException("Path was not known");
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }
}
