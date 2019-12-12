package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.StudyAdminDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.FireCloudException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.export.ExportStudyPayload;
import org.broadinstitute.ddp.service.FireCloudExportService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class ExportStudyRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(ExportStudyRoute.class);
    private final FireCloudExportService fireCloudExportService;
    private final StudyAdminDao studyAdminDao;

    private final Gson gson = new Gson();

    public ExportStudyRoute(FireCloudExportService fireCloudExportService, StudyAdminDao studyAdminDao) {
        this.fireCloudExportService = fireCloudExportService;
        this.studyAdminDao = studyAdminDao;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        JsonElement data = new JsonParser().parse(request.body());
        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
        }
        JsonObject payload = data.getAsJsonObject();

        String userGuid = RouteUtil.getDDPAuth(request).getOperator();
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        String workspaceNamespace = null;
        if (payload.has(ExportStudyPayload.WORKSPACE_NAMESPACE)
                && !payload.get(ExportStudyPayload.WORKSPACE_NAMESPACE).isJsonNull()) {
            workspaceNamespace = payload.get(ExportStudyPayload.WORKSPACE_NAMESPACE).getAsString();
        } else {
            ResponseUtil.haltError(response, 400,
                    new ApiError(ErrorCodes.FireCloudErrors.MISSING_WORKSPACE_NAMESPACE,
                            "no workspace namespace submitted"));
        }
        String workspaceName = null;
        if (payload.has(ExportStudyPayload.WORKSPACE_NAME)
                && !payload.get(ExportStudyPayload.WORKSPACE_NAME).isJsonNull()) {
            workspaceName = payload.get(ExportStudyPayload.WORKSPACE_NAME).getAsString();
        } else {
            ResponseUtil.haltError(response, 400,
                    new ApiError(ErrorCodes.FireCloudErrors.MISSING_WORKSPACE_NAME, "no workspace name submitted"));
        }

        Date includeAfterDate = new Date(0);
        if (payload.has(ExportStudyPayload.INCLUDE_AFTER_DATE)
                && !payload.get(ExportStudyPayload.INCLUDE_AFTER_DATE).isJsonNull()) {
            includeAfterDate = gson.fromJson("\""
                    + payload.get(ExportStudyPayload.INCLUDE_AFTER_DATE).getAsString() + "\"", Date.class);
        } else {
            ResponseUtil.haltError(response, 400,
                    new ApiError(ErrorCodes.FireCloudErrors.INVALID_AFTER_DATE, "no after date submitted"));
        }


        ExportStudyPayload exportStudyPayload = gson.fromJson(payload, ExportStudyPayload.class);

        LOG.info("Exporting study {} to FireCloud workspace {}/{} for data after {}",
                studyGuid, workspaceNamespace, workspaceName, includeAfterDate);
        Collection<String> participantIds;
        try {
            String finalWorkspaceNamespace = workspaceNamespace;
            String finalWorkspaceName = workspaceName;
            participantIds = TransactionWrapper.withTxn((Handle handle) -> {
                try {
                    String fireCloudServiceAccountPath =
                            studyAdminDao.getServiceAccountPath(handle, userGuid, studyGuid);
                    return fireCloudExportService.exportStudy(handle, exportStudyPayload,
                            studyGuid, fireCloudServiceAccountPath);
                } catch (FireCloudException e) {
                    response.status(502);
                    LOG.error(e.getMessage());
                    return null;
                } catch (IOException e) {
                    throw new DDPException("error exporting study " + studyGuid + " to FireCloud workspace "
                            + finalWorkspaceNamespace + "/" + finalWorkspaceName, e);
                }
            });
        } catch (RuntimeException e) {
            throw new DDPException("error adding study to FireCloud", e);
        }

        return participantIds == null ? null : participantIds.toArray();
    }
}
