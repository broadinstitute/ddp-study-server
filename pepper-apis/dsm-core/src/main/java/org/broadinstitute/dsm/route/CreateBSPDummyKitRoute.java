package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dao.kit.BSPDummyKitDao;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class CreateBSPDummyKitRoute implements Route {
    private static final String DUMMY_KIT_TYPE_NAME = "DUMMY_KIT_TYPE";
    private static final String DUMMY_REALM_NAME = "DUMMY_KIT_REALM";
    private static final String USER_ID = "74";
    private static final Logger logger = LoggerFactory.getLogger(CreateBSPDummyKitRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        logger.info("Got Mercury Test request");
        String kitLabel = request.params(RequestParameter.LABEL);
        if (StringUtils.isBlank(kitLabel)) {
            // return bad request
            response.status(500);
            logger.error("Bad request from Mercury! Should include a kitlabel");
            return null;
        }
        logger.info("Found kitlabel " + kitLabel + " in Mercury request");
        int ddpInstanceId = (int) DBUtil.getBookmark(DUMMY_REALM_NAME);
        logger.info("Found ddp instance id for mock test " + ddpInstanceId);
        DDPInstance mockDdpInstance = DDPInstance.getDDPInstanceById(ddpInstanceId);
        if (mockDdpInstance != null) {
            logger.info("Found mockDdpInstance " + mockDdpInstance.getName());
            String mercuryKitRequestId = "MERCURY_" + KitRequestShipping.createRandom(20);
            int kitTypeId = (int) DBUtil.getBookmark(DUMMY_KIT_TYPE_NAME);
            logger.info("Found kit type for Mercury Dummy Endpoint " + kitTypeId);
            String ddpParticipantId = new BSPDummyKitDao().getRandomParticipantForStudy(mockDdpInstance);
            String participantCollaboratorId =
                    KitRequestShipping.getCollaboratorParticipantId(mockDdpInstance, ddpParticipantId, "", null);
            String collaboratorSampleId =
                    KitRequestShipping.getCollaboratorSampleId(kitTypeId, participantCollaboratorId, DUMMY_KIT_TYPE_NAME, mockDdpInstance);
            if (ddpParticipantId != null) {
                //if instance not null
                TransactionWrapper.inTransaction(conn -> {
                    String dsmKitRequestId = KitRequestShipping.writeRequest(conn, mockDdpInstance.getDdpInstanceId(), mercuryKitRequestId,
                            kitTypeId, ddpParticipantId, participantCollaboratorId,
                            collaboratorSampleId, USER_ID, "", "", "", false, "",
                            null, DUMMY_KIT_TYPE_NAME, null, false, null);
                    new BSPDummyKitDao().updateKitLabel(kitLabel, dsmKitRequestId);
                    return null;
                });

            }
            logger.info("Returning 200 to Mercury");
            response.status(200);
        } else {
            logger.error("Returning 500 to Mercury");
            response.status(500);
        }
        return null;
    }


}
