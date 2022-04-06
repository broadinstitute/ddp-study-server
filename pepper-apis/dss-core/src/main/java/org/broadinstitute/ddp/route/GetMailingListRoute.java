package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiMailingList.MailingListEntryDto;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.mailinglist.GetMailingListResponse;
import org.broadinstitute.ddp.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetMailingListRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetMailingListRoute.class);

    @Override
    public List<GetMailingListResponse> handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        return TransactionWrapper.withTxn(
                handle -> {
                    if (handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid) == null) {
                        String errMsg = "A study with GUID " + studyGuid + " you try to get data for is not found";
                        ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }
                    List<MailingListEntryDto> mailingListEntries = handle.attach(JdbiMailingList.class).findByStudy(studyGuid);
                    LOG.info(
                            "Found {} mailing list mailingListEntries for the study {}",
                            mailingListEntries.size(), studyGuid
                    );
                    return mailingListEntries.stream().map(e -> new GetMailingListResponse(e)).collect(Collectors.toList());
                }
        );
    }

}
