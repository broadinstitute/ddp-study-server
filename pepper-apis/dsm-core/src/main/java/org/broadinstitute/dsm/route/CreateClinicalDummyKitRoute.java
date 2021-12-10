package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.kit.BSPDummyKitDao;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.List;
import java.util.Optional;

public class CreateClinicalDummyKitRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(CreateClinicalDummyKitRoute.class);
    private static String CLINICAL_KIT_REALM = "CLINICAL_KIT_REALM";
    private static String CLINICAL_KIT_PREFIX = "CLINICALKIT_";
    private int REALM;
    private static final String USER_ID = "MERCURY";

    @Override
    public Object handle(Request request, Response response) {
        String kitLabel = request.params(RequestParameter.LABEL);
        String kitTypeString = request.params(RequestParameter.KIT_TYPE);
        if (StringUtils.isBlank(kitLabel)) {
            logger.warn("Got a create Clinical Kit request without a kit label!!");
            response.status(500);
            return "Please include a kit label as a path parameter";
        }
        logger.info("Got a new Clinical Kit request with kit label " + kitLabel+" and kit type "+kitTypeString);
        new BookmarkDao().getBookmarkByInstance(CLINICAL_KIT_REALM).ifPresentOrElse(book -> {
            REALM = (int) book.getValue();
        }, () -> {
            throw new RuntimeException("Bookmark doesn't exist for " + CLINICAL_KIT_REALM);
        });
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(REALM);
        if (ddpInstance != null) {
            String kitRequestId = CLINICAL_KIT_PREFIX + KitRequestShipping.createRandom(20);
            String ddpParticipantId = new BSPDummyKitDao().getRandomParticipantForStudy(ddpInstance);
            Optional<ElasticSearchParticipantDto> maybeParticipantByParticipantId = ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
            List<KitType> kitTypes = KitType.getKitTypes(ddpInstance.getName(), null);
            KitType desiredKitType = kitTypes.stream().filter(k -> kitTypeString.equalsIgnoreCase(k.getName())).findFirst().orElseThrow();
            logger.info("Found kit type " + desiredKitType.getName());
            maybeParticipantByParticipantId.ifPresentOrElse(p -> {
                String participantCollaboratorId = KitRequestShipping.getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                        ddpInstance.getCollaboratorIdPrefix(), ddpParticipantId, p.getProfile().map(ESProfile::getHruid).orElseThrow(), null);
                String collaboratorSampleId = KitRequestShipping.getCollaboratorSampleId(desiredKitType.getKitId(), participantCollaboratorId, desiredKitType.getName());
                logger.info("Found collaboratorSampleId  " + collaboratorSampleId);
                //if instance not null
                String dsmKitRequestId = KitRequestShipping.writeRequest(ddpInstance.getDdpInstanceId(), kitRequestId, desiredKitType.getKitId(),
                        ddpParticipantId, participantCollaboratorId, collaboratorSampleId,
                        USER_ID, "", "", "", false, "");
                new BSPDummyKitDao().updateKitLabel(kitLabel, dsmKitRequestId);

            }, () -> {
                throw new RuntimeException(" Participant " + ddpParticipantId + " was not found!");
            });
            logger.info("Kit added successfully");
            response.status(200);
            return null;

        }
        logger.error("Error occurred while adding kit");
        response.status(500);
        return null;
    }

}
