package org.broadinstitute.dsm.route;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.TissueSmId;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.kit.BSPDummyKitDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
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
    private final String ffpeUser = "ffpe-dummy-kit-creator";
    private final String ffpe = "ffpe";
    private final String ffpeScroll = "ffpe-scroll";
    private final String ffpeSection = "ffpe-section";
    private int realm;

    @Override
    public Object handle(Request request, Response response) {
        String kitLabel = request.params(RequestParameter.LABEL);
        String kitTypeString = request.params(RequestParameter.KIT_TYPE);
        if (StringUtils.isBlank(kitLabel)) {
            logger.warn("Got a create Clinical Kit request without a kit label!!");
            response.status(500);
            return "Please include a kit label as a path parameter";
        }
        logger.info("Got a new Clinical Kit request with kit label " + kitLabel + " and kit type " + kitTypeString);
        new BookmarkDao().getBookmarkByInstance(CLINICAL_KIT_REALM).ifPresentOrElse(book -> {
            realm = (int) book.getValue();
        }, () -> {
                throw new RuntimeException("Bookmark doesn't exist for " + CLINICAL_KIT_REALM);
            });
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(realm).orElseThrow();
        BSPDummyKitDao bspDummyKitDao = new BSPDummyKitDao();
        if (ddpInstanceDto != null) {
            String kitRequestId = CLINICAL_KIT_PREFIX + KitRequestShipping.createRandom(20);
            String ddpParticipantId = new BSPDummyKitDao().getRandomParticipantForStudy(ddpInstanceDto);
            Optional<ElasticSearchParticipantDto> maybeParticipantByParticipantId =
                    ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
            List<KitType> kitTypes = KitType.getKitTypes(ddpInstanceDto.getInstanceName(), null);
            KitType desiredKitType = kitTypes.stream().filter(k -> kitTypeString.equalsIgnoreCase(k.getName())).findFirst().orElseThrow();
            logger.info("Found kit type " + desiredKitType.getName());

            if (maybeParticipantByParticipantId.isEmpty()) {
                throw new RuntimeException("PT not found " + ddpParticipantId);
            }

            if (kitTypeString.toLowerCase().indexOf(ffpe) == -1) {
                String participantCollaboratorId =
                        KitRequestShipping.getCollaboratorParticipantId(ddpInstanceDto.getBaseUrl(),
                                String.valueOf(ddpInstanceDto.getDdpInstanceId()),
                                ddpInstanceDto.getMigratedDdp(), ddpInstanceDto.getCollaboratorIdPrefix(), ddpParticipantId,
                                maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid).orElseThrow(), null);
                String collaboratorSampleId =
                        KitRequestShipping.getCollaboratorSampleId(desiredKitType.getKitId(), participantCollaboratorId,
                                desiredKitType.getName());
                logger.info("Found collaboratorSampleId  " + collaboratorSampleId);
                //if instance not null
                String dsmKitRequestId =
                        KitRequestShipping.writeRequest(ddpInstanceDto.getDdpInstanceId(), kitRequestId, desiredKitType.getKitId(),
                                ddpParticipantId, participantCollaboratorId, collaboratorSampleId, USER_ID, "", "", "", false, "",
                                ddpInstanceDto);
                bspDummyKitDao.updateKitLabel(kitLabel, dsmKitRequestId);
            } else {
                String smIdType;
                if (kitTypeString.equalsIgnoreCase(ffpeScroll)) {
                    smIdType = TissueSmId.SCROLLS;
                } else if (kitTypeString.equalsIgnoreCase(ffpeSection)) {
                    smIdType = TissueSmId.USS;
                } else {
                    throw new RuntimeException("The FFPE kit type does not match any of the valid types " + kitTypeString);
                }
                int tries = 0;
                String randomOncHistoryDetailId = bspDummyKitDao.getRandomOncHistoryForStudy(ddpInstanceDto.getInstanceName());
                OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId,
                        ddpInstanceDto.getInstanceName());
                ddpParticipantId = oncHistoryDetail.getDdpParticipantId();
                Optional<ElasticSearchParticipantDto> maybeParticipant =
                        ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
                logger.info("found randomOncHistoryDetailId " + randomOncHistoryDetailId);
                logger.info("found short id " + maybeParticipant.get().getProfile().map(ESProfile::getHruid));
                while (tries < 10 && (oncHistoryDetail == null || StringUtils.isBlank(oncHistoryDetail.getAccessionNumber())
                        || maybeParticipant.isEmpty() || maybeParticipant.get().getProfile().map(ESProfile::getHruid).isEmpty())) {
                    randomOncHistoryDetailId = bspDummyKitDao.getRandomOncHistoryForStudy(ddpInstanceDto.getInstanceName());
                    oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId, ddpInstanceDto.getInstanceName());
                    ddpParticipantId = oncHistoryDetail.getDdpParticipantId();
                    maybeParticipant =
                            ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId);
                    logger.info("found randomOncHistoryDetailId " + randomOncHistoryDetailId);
                    logger.info("found short id " + maybeParticipant.get().getProfile().map(ESProfile::getHruid));
                    tries++;
                }
                if (tries >= 10) {
                    throw new RuntimeException("couldn't find a valid onc history to create dummy");
                }
                List<Tissue> tissueIds =
                        OncHistoryDetail.getOncHistoryDetail(randomOncHistoryDetailId, ddpInstanceDto.getInstanceName()).getTissues();
                String tissueId;

                if (tissueIds.isEmpty()) {
                    tissueId = Tissue.createNewTissue(randomOncHistoryDetailId, ffpeUser);
                } else {
                    tissueId = String.valueOf(tissueIds.get(new Random().nextInt(tissueIds.size())).getTissueId());
                }
                new TissueSMIDDao().createNewSMIDForTissueWithValue(tissueId, ffpeUser, smIdType, kitLabel);
            }
            logger.info("Kit added successfully");
            response.status(200);
            return null;

        }
        logger.error("Error occurred while adding kit");
        response.status(500);
        return null;
    }

}
