package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetShouldDisplayLanguageChangePopupRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetShouldDisplayLanguageChangePopupRoute.class);

    @Override public Object handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        LOG.info("Received request for whether to display the language change popup for study {}", studyGuid);

        var result = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            long umbrellaStudyId = studyDto.getId();

            return handle.attach(StudyDao.class)
                .findSettings(umbrellaStudyId)
                .map(StudySettings::shouldDisplayLanguageChangePopup)
                .orElse(false);
        });

        response.status(HttpStatus.SC_OK);
        return result;
    }
}
