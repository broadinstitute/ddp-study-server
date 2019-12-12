package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.StudyAdminDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetAdminStudiesRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetAdminStudiesRoute.class);
    private final StudyAdminDao studyAdminDao;


    public GetAdminStudiesRoute(StudyAdminDao studyAdminDao) {
        this.studyAdminDao = studyAdminDao;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {

        String guid = RouteUtil.getDDPAuth(request).getOperator();
        LOG.info("Retrieving studies that user has admin control over for {}", guid);

        if (StringUtils.isBlank(guid)) {
            String msg = "Don't have user guid";
            ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.MISSING_USER_GUID, msg));
        }

        return TransactionWrapper.withTxn((Handle handle) -> studyAdminDao.getStudies(handle, guid));
    }
}
