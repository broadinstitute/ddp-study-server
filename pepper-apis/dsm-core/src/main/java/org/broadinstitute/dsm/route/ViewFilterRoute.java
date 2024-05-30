package org.broadinstitute.dsm.route;

import java.util.Collection;

import com.google.gson.Gson;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.DuplicateEntityException;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

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
            instance = DDPInstance.getDDPInstance(realm);
        } else {
            throw new RuntimeException("No realm is sent!");
        }
        String userIdRequest = null;
        if (queryParams.value(UserUtil.USER_ID) != null) {
            userIdRequest = UserUtil.getUserId(request);
        }
        if (UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest)
                || UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdRequest)) {
            String json = request.body();
            if (request.url().contains(RoutePath.GET_FILTERS)) {
                if (StringUtils.isNotBlank(realm)) {
                    String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                    return ViewFilter.getAllFilters(userIdRequest, patchUtil.getColumnNameMap(), parent, ddpGroupId,
                            instance.getDdpInstanceId());
                } else {
                    if (!request.url().contains(RoutePath.GET_DEFAULT_FILTERS)) {
                        Collection<String> realms = UserUtil.getListOfAllowedRealms(userIdRequest);
                        realm = realms.iterator().next();
                        String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                        return ViewFilter.getAllFilters(userIdRequest, patchUtil.getColumnNameMap(), parent, ddpGroupId,
                                instance.getDdpInstanceId());
                    }
                }
            } else if (request.url().contains(RoutePath.SAVE_FILTER)) {
                String ddpGroupId = DDPInstance.getDDPGroupId(realm);
                ViewFilter viewFilter = new Gson().fromJson(json, ViewFilter.class);

                String filterName = viewFilter.getFilterName();
                if (ViewFilter.doesFilterExist(filterName)) {
                    // tell the user to retry with a different name for the filter if the name is already taken
                    throw new DuplicateEntityException("filter name", filterName);
                }
                return ViewFilter.saveFilter(viewFilter, userIdRequest, patchUtil.getColumnNameMap(), ddpGroupId);
            } else if (request.url().contains(RoutePath.FILTER_DEFAULT)) {
                if (StringUtils.isBlank(parent)) {
                    throw new RuntimeException("No parent was sent in the request.");
                }
                String userMail = queryParams.get(RequestParameter.USER_MAIL).value();
                String filterName = queryParams.get(RequestParameter.FILTER_NAME).value();
                return ViewFilter.setDefaultFilter(filterName, userMail, parent);
            }
            throw new RuntimeException("Path was not known");
        } else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }
}
