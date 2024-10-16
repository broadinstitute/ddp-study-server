package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmOnDemandActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.TriggeredInstance;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
public class GetDsmTriggeredInstancesRoute implements Route {
    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String activityCode = request.params(PathParam.ACTIVITY_CODE);

        log.info("Attempting to find list of dsm triggered activity instances for study guid {} and activity code {}",
                studyGuid, activityCode);

        return TransactionWrapper.withTxn(handle -> {
            long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                        log.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return -1L;     // Not reached
                    });

            long activityId = handle.attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyId, activityCode)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, String.format(
                                "Could not find activity with code %s for study with guid %s", activityCode, studyGuid));
                        log.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return -1L;     // Not reached
                    });

            List<TriggeredInstance> instances = handle.attach(DsmOnDemandActivityDao.class)
                    .findAllTriggeredInstancesByStudyAndActivity(studyId, activityId);
            log.info("Found {} triggered instances for study guid {} and activity code {}",
                    instances.size(), studyGuid, activityCode);

            return instances;
        });
    }
}
