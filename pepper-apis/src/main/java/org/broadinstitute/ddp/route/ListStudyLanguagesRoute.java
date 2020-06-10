package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class ListStudyLanguagesRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(ListStudyLanguagesRoute.class);

    @Override public Object handle(Request request, Response response) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        LOG.info("Received request for supported languages for study {}", studyGuid);

        var result = TransactionWrapper.withTxn(handle -> {
            //Get the umbrella study id
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            long umbrellaStudyId = studyDto.getId();
            var studyLanguageDao = handle.attach(StudyLanguageDao.class);
            return studyLanguageDao.findLanguages(umbrellaStudyId);
        });

        response.status(HttpStatus.SC_OK);
        return result;
    }
}
