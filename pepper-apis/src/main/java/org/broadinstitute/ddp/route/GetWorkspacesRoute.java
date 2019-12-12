package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.util.List;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.StudyAdminDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.FireCloudException;
import org.broadinstitute.ddp.json.export.Workspace;
import org.broadinstitute.ddp.service.FireCloudExportService;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetWorkspacesRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetWorkspacesRoute.class);
    private final FireCloudExportService fireCloudExportService;
    private final StudyAdminDao studyAdminDao;

    public GetWorkspacesRoute(FireCloudExportService fireCloudExportService, StudyAdminDao studyAdminDao) {
        this.fireCloudExportService = fireCloudExportService;
        this.studyAdminDao = studyAdminDao;
    }

    @Override
    public Object handle(Request request, Response response) throws IOException, InterruptedException {
        String userGuid = RouteUtil.getDDPAuth(request).getOperator();
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        LOG.info("Retrieving workspaces that user as given by the firecloud token: " + userGuid
                + "has writer or owner access level");

        List<Workspace> workspaces = TransactionWrapper.withTxn((Handle handle) -> {
            try {
                String fireCloudServiceAccountPath = studyAdminDao.getServiceAccountPath(handle, userGuid, studyGuid);
                try {
                    return fireCloudExportService.getWorkspaces(fireCloudServiceAccountPath);
                } catch (FireCloudException e) {
                    response.status(502);
                    LOG.error(e.getMessage());
                    return null;
                }
            } catch (IOException e) {
                throw new RuntimeException("couldn't find workspaces for study: " + studyGuid
                        + " and user: " + userGuid, e);
            }
        });

        return workspaces;
    }
}
