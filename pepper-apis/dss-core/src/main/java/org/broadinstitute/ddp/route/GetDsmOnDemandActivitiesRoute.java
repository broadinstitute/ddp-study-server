package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;

import java.util.List;

import org.apache.http.HttpStatus;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmOnDemandActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.OnDemandActivity;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetDsmOnDemandActivitiesRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmOnDemandActivitiesRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);

        LOG.info("Attempting to find list of on-demand activities for study guid {}", studyGuid);

        return TransactionWrapper.withTxn(handle -> {
            long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                        LOG.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return -1L;     // Not reached
                    });

            List<OnDemandActivity> activities = handle.attach(DsmOnDemandActivityDao.class)
                    .findAllOrderedOndemandActivitiesByStudy(studyId);
            LOG.info("Found {} on-demand activities for study guid {}", activities.size(), studyGuid);

            return activities;
        });
    }
}
