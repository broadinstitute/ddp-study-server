package org.broadinstitute.dsm.route;

import java.util.Optional;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.kit.ClinicalKitDao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.ClinicalKitWrapper;
import org.broadinstitute.dsm.model.gp.GPReceivedKit;
import org.broadinstitute.dsm.model.gp.KitInfo;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class ClinicalKitsRoute implements Route {

    private static final Logger logger = LoggerFactory.getLogger(ClinicalKitsRoute.class);

    public static final String MERCURY = "MERCURY";
    private final String getEventQuery = "SELECT ddp_participant_id, ddp.ddp_instance_id, instance_name, base_url, event_name, "
            + "event_type, auth0_token, sm_id_value " + "FROM sm_id sm LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
            + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
            + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
            + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
            + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
            + "LEFT JOIN  event_type eve on (eve.kit_type_id = sit.kit_type_id and ddp.ddp_instance_id = eve.ddp_instance_id) "
            + "WHERE sm.sm_id_value = ? AND NOT sm.deleted <=> 1";

    private NotificationUtil notificationUtil;

    public ClinicalKitsRoute(@NonNull NotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
    }

    @Override
    public Object handle(Request request, Response response) {
        String kitLabel = request.params(RequestParameter.LABEL);
        if (StringUtils.isBlank(kitLabel)) {
            throw new RuntimeException("Please include a kit label as a path parameter");
        }
        BSPKit bspKit = new BSPKit();
        Optional<BSPKitDto> optionalBSPKitDto = bspKit.canReceiveKit(kitLabel);

        //kit found in ddp_kit table
        if (!optionalBSPKitDto.isEmpty()) {
            //check if kit is from a pt which is withdrawn
            Optional<BSPKitStatus> result = bspKit.getKitStatus(optionalBSPKitDto.get(), notificationUtil);
            if (!result.isEmpty()) {
                return result.get();
            }
        }
        return getClinicalKit(kitLabel, optionalBSPKitDto);
    }

    private ClinicalKitDto getClinicalKit(String kitLabel, Optional<BSPKitDto> optionalBSPKitDto) {
        logger.info("Checking label " + kitLabel);
        if (optionalBSPKitDto.isEmpty()) {
            logger.info("Checking the kit for SM Id value " + kitLabel);
            Optional<ClinicalKitWrapper> maybeClinicalKitWrapper = ClinicalKitDao.getClinicalKitFromSMId(kitLabel);
            maybeClinicalKitWrapper.orElseThrow();
            ClinicalKitWrapper clinicalKitWrapper = maybeClinicalKitWrapper.get();
            ClinicalKitDto clinicalKitDto = clinicalKitWrapper.getClinicalKitDto();
            DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(clinicalKitWrapper.getDdpInstanceId());
            clinicalKitDto.setNecessaryParticipantDataToClinicalKit(clinicalKitWrapper.getDdpParticipantId(), ddpInstance);
            if (StringUtils.isNotBlank(clinicalKitDto.getAccessionNumber())) {
                ClinicalKitDao.setAccessionTimeForSMID(kitLabel);
                try {
                    TransactionWrapper.inTransaction(conn -> {
                        KitDDPNotification kitDDPNotification =
                                KitDDPNotification.getKitDDPNotificationForTissue(getEventQuery, kitLabel, 1);
                        if (kitDDPNotification != null && StringUtils.isNotBlank(kitDDPNotification.getEventName())
                                && StringUtils.isNotBlank(kitDDPNotification.getEventType())) {
                            EventUtil.triggerDDP(conn, kitDDPNotification);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    logger.error("Failed to trigger DDP");
                }
                return clinicalKitDto;
            }
            throw new RuntimeException("The kit doesn't have an accession number! SM ID is: " + kitLabel);
        } else {
            Optional<KitInfo> maybeKitInfo = GPReceivedKit.receiveKit(kitLabel, optionalBSPKitDto.orElseThrow(), notificationUtil, MERCURY);
            KitInfo kitInfo = maybeKitInfo.orElseThrow();
            ClinicalKitDto clinicalKit = new ClinicalKitDto();
            logger.info("Creating clinical kit to return to GP " + kitLabel);
            clinicalKit.setCollaboratorParticipantId(kitInfo.getCollaboratorParticipantId());
            clinicalKit.setSampleId(kitInfo.getCollaboratorSampleId());
            clinicalKit.setMaterialType(kitInfo.getMaterialInfo());
            clinicalKit.setVesselType(kitInfo.getReceptacleName());
            clinicalKit.setSampleType(kitInfo.getKitType());
            clinicalKit.setMfBarcode(kitLabel);
            clinicalKit.setCollectionDate(kitInfo.getCollectionDate());
            clinicalKit.setSampleCollection(ClinicalKitDao.PECGS);
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(kitInfo.getRealm());
            clinicalKit.setNecessaryParticipantDataToClinicalKit(optionalBSPKitDto.get().getDdpParticipantId(), ddpInstance);
            return clinicalKit;
        }
    }

}
