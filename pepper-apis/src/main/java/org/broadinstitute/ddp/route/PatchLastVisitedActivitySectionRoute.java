package org.broadinstitute.ddp.route;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;


public class PatchLastVisitedActivitySectionRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PatchLastVisitedActivitySectionRoute.class);
    private ActivityInstanceService actInstService;

    public PatchLastVisitedActivitySectionRoute(ActivityInstanceService actInstService) {
        this.actInstService = actInstService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);
        JsonElement data = new JsonParser().parse(request.body());

        LOG.info("Request to update last visited section on instance {} for participant {} in study {}",
                instanceGuid, userGuid, studyGuid);

        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
        }

        TransactionWrapper.useTxn(handle -> {
            JsonObject payload = data.getAsJsonObject();
            JsonElement lastVisitedActivitySectionValue = payload.get("lastVisitedActivitySection");
            int lastVisitedActivitySection = lastVisitedActivitySectionValue.getAsInt();
            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, userGuid, studyGuid, instanceGuid);

            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            String isoLangCode = preferredUserLanguage.getIsoCode();

            ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
            LOG.info("Using ddp content style {} to format activity content", style);

            LOG.info("Attempting to find a translation for the following language: {}", isoLangCode);
            Optional<ActivityInstance> inst = actInstService.getTranslatedActivity(
                    handle, userGuid, instanceDto.getActivityType(), instanceGuid, isoLangCode, style
            );

            if (!inst.isPresent()) {
                response.status(HttpStatus.SC_NOT_FOUND);
            }
            handle.attach(ActivityInstanceDao.class)
                    .updateLastVisitedSectionByInstanceGuid(instanceGuid, lastVisitedActivitySection);
        });
        response.status(HttpStatus.SC_OK);
        return "";
    }
}
