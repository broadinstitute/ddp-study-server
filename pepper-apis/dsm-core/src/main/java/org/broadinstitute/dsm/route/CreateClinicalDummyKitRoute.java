package org.broadinstitute.dsm.route;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.kit.BSPDummyKitDao;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class CreateClinicalDummyKitRoute implements Route {
    private static final Logger logger = LoggerFactory.getLogger(CreateClinicalDummyKitRoute.class);
    private static final String USER_ID = "MERCURY";
    private static String CLINICAL_KIT_REALM = "CLINICAL_KIT_REALM";
    private static String CLINICAL_KIT_PREFIX = "CLINICALKIT_";
    private static final String ffpeUser = "ffpe-dummy-kit-creator";
    private final String ffpe = "ffpe";
    private final String ffpeScroll = "ffpe-scroll";
    private final String ffpeSection = "ffpe-section";
    private int realm;
    private OncHistoryDetailDaoImpl oncHistoryDetailDaoImpl;
    private ParticipantDao participantDao;
    private boolean fixedParticipantId = false;

    public CreateClinicalDummyKitRoute(OncHistoryDetailDaoImpl oncHistoryDetailDao) {
        this.oncHistoryDetailDaoImpl = oncHistoryDetailDao;
        participantDao = new ParticipantDao();
    }


    public static void addCollaboratorSampleId(String tissueId, DDPInstance ddpInstance, String ddpParticipantId, String shortId) {
        String collaboratorParticipantId = KitRequestShipping
                .getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                        ddpInstance.getCollaboratorIdPrefix(), ddpParticipantId, shortId, "4");
        updateTissue(tissueId, collaboratorParticipantId + "_T1");

    }

    private static void updateTissue(String tissueId, String collaboratorSampleId) {
        String name = "t.collaboratorSampleId";
        NameValue nameValue = new NameValue(name, collaboratorSampleId);
        DBElement dbElement = PatchUtil.getColumnNameMap().get(name);
        Patch.patch(tissueId, ffpeUser, nameValue, dbElement);
    }

    @Override
    public Object handle(Request request, Response response) {
        fixedParticipantId = false;
        String kitLabel = request.params(RequestParameter.LABEL);
        String kitTypeString = request.params(RequestParameter.KIT_TYPE);
        String participantId = request.params(RequestParameter.PARTICIPANTID);
        String ddpParticipantId;
        Optional<ElasticSearchParticipantDto> maybeParticipantByParticipantId;
        if (StringUtils.isBlank(kitLabel)) {
            logger.warn("Got a create Clinical Kit request without a kit label!!");
            response.status(500);
            return "Please include a kit label as a path parameter";
        }
        logger.info("Got a new Clinical Kit request with kit label " + kitLabel + " and kit type " + kitTypeString);
        new BookmarkDao().getBookmarkByInstance(CLINICAL_KIT_REALM).ifPresentOrElse(book -> {
                    realm = (int) book.getValue();
                },
                () -> {
                    throw new RuntimeException("Bookmark doesn't exist for " + CLINICAL_KIT_REALM);
                });
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(realm);
        BSPDummyKitDao bspDummyKitDao = new BSPDummyKitDao();
        if (ddpInstance != null) {
            String kitRequestId = CLINICAL_KIT_PREFIX + KitRequestShipping.createRandom(20);
            if (StringUtils.isBlank(participantId)) {
                int tries = 0;
                ddpParticipantId = new BSPDummyKitDao().getRandomParticipantForStudy(ddpInstance);
                maybeParticipantByParticipantId =
                        ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
                if (maybeParticipantByParticipantId.isEmpty()) {
                    throw new RuntimeException("PT not found " + ddpParticipantId);
                }
                while (!maybeParticipantByParticipantId.get().getStatus().get().equals("ENROLLED") && tries < 10) {
                    tries++;
                    ddpParticipantId = new BSPDummyKitDao().getRandomParticipantForStudy(ddpInstance);
                    maybeParticipantByParticipantId =
                            ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
                }
                if (tries == 10) {
                    throw new RuntimeException("No enrolled participants found");
                }
            } else {
                fixedParticipantId = true;
                Optional<String> maybeParticipantId =
                        participantDao.getParticipantFromCollaboratorParticipantId(participantId);
                maybeParticipantId.orElseThrow();
                ddpParticipantId = maybeParticipantId.get();
                maybeParticipantByParticipantId =
                        ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(),
                                ddpParticipantId);
            }
            List<KitType> kitTypes = KitType.getKitTypes(ddpInstance.getName(), null);
            KitType desiredKitType = kitTypes.stream().filter(k -> kitTypeString.equalsIgnoreCase(k.getName())).findFirst().orElseThrow();
            logger.info("Found kit type " + desiredKitType.getName());

            if (kitTypeString.toLowerCase().indexOf(ffpe) == -1) {
                String participantCollaboratorId = KitRequestShipping
                        .getCollaboratorParticipantId(ddpInstance.getBaseUrl(), ddpInstance.getDdpInstanceId(), ddpInstance.isMigratedDDP(),
                                ddpInstance.getCollaboratorIdPrefix(), ddpParticipantId,
                                maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid).orElseThrow(), null);
                String collaboratorSampleId = KitRequestShipping
                        .getCollaboratorSampleId(desiredKitType.getKitId(), participantCollaboratorId, desiredKitType.getName());
                logger.info("Found collaboratorSampleId  " + collaboratorSampleId);
                //if instance not null
                String dsmKitRequestId = KitRequestShipping
                        .writeRequest(ddpInstance.getDdpInstanceId(), kitRequestId, desiredKitType.getKitId(), ddpParticipantId,
                                participantCollaboratorId, collaboratorSampleId, USER_ID, "", "", "", false, "", ddpInstance);
                bspDummyKitDao.updateKitLabel(kitLabel, dsmKitRequestId);
            } else {

                String smIdType;
                if (kitTypeString.equalsIgnoreCase(ffpeScroll)) {
                    smIdType = SmId.SCROLLS;
                } else if (kitTypeString.equalsIgnoreCase(ffpeSection)) {
                    smIdType = SmId.USS;
                } else {
                    throw new RuntimeException("The FFPE kit type does not match any of the valid types " + kitTypeString);
                }
                String randomOncHistoryDetailId;
                OncHistoryDetail oncHistoryDetail;

                if (fixedParticipantId) {
                    randomOncHistoryDetailId =
                            bspDummyKitDao.getRandomOncHistoryForParticipant(ddpInstance.getName(), ddpParticipantId);
                    if (StringUtils.isBlank(randomOncHistoryDetailId)) {
                        return "Participant doesn't have an eligible onc history/tissue";
                    }
                    oncHistoryDetail =
                            OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId, ddpInstance.getName());
                    logger.info("found randomOncHistoryDetailId " + randomOncHistoryDetailId + " for participant " + ddpParticipantId);
                } else {
                    int tries = 0;
                    randomOncHistoryDetailId = bspDummyKitDao.getRandomOncHistoryForStudy(ddpInstance.getName());
                    oncHistoryDetail =
                            OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId, ddpInstance.getName());
                    ddpParticipantId = oncHistoryDetail.getDdpParticipantId();
                    maybeParticipantByParticipantId =
                            ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
                    logger.info("found randomOncHistoryDetailId " + randomOncHistoryDetailId);
                    logger.info("found short id " + maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid));
                    while (tries < 10 && (oncHistoryDetail == null || StringUtils.isBlank(oncHistoryDetail.getAccessionNumber())
                            || maybeParticipantByParticipantId.isEmpty()
                            || maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid).isEmpty())) {
                        randomOncHistoryDetailId = bspDummyKitDao.getRandomOncHistoryForStudy(ddpInstance.getName());
                        oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId, ddpInstance.getName());
                        ddpParticipantId = oncHistoryDetail.getDdpParticipantId();
                        maybeParticipantByParticipantId =
                                ElasticSearchUtil
                                        .getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
                        logger.info("found randomOncHistoryDetailId " + randomOncHistoryDetailId);
                        logger.info("found short id " + maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid));
                        tries++;
                    }
                    if (tries >= 10) {
                        throw new RuntimeException("couldn't find a valid onc history to create dummy");
                    }
                }
                List<Tissue> tissueIds =
                        oncHistoryDetailDaoImpl.getRandomOncHistoryDetail(randomOncHistoryDetailId, ddpInstance.getName()).getTissues();
                String tissueId;

                if (tissueIds.isEmpty()) {
                    tissueId = Tissue.createNewTissue(randomOncHistoryDetailId, ffpeUser);
                    String shortId = maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid).get();
                    addCollaboratorSampleId(tissueId, ddpInstance, ddpParticipantId, shortId);
                } else {
                    tissueId = String.valueOf(tissueIds.get(new Random().nextInt(tissueIds.size())).getTissueId());
                }
                new TissueSMIDDao().createNewSMIDForTissueWithValue(tissueId, ffpeUser, smIdType, kitLabel);

                logger.info("Kit added successfully");
                response.status(200);
                return null;
            }

        }
        logger.error("Error occurred while adding kit");
        response.status(500);
        return null;
    }


}
