package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.kit.ClinicalKitDao;
import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.model.gp.BSPKit;
import org.broadinstitute.dsm.model.gp.GPReceivedKit;
import org.broadinstitute.dsm.model.gp.KitInfo;
import org.broadinstitute.dsm.model.gp.bsp.BSPKitStatus;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Optional;

public class ClinicalKitsRoute implements Route {
    private String FIRSTNAME = "firstName";
    private String LASTNAME = "lastName";
    private String DATE_OF_BIRtH = "dateOfBirth";

    private static final Logger logger = LoggerFactory.getLogger(ClinicalKitsRoute.class);

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
            //kit not found in ddp_kit table -> check tissue smi-ids
            return new ClinicalKitDao().getClinicalKitBasedOnSmId(kitLabel);
        } else {
            Optional<KitInfo> maybeKitInfo = GPReceivedKit.receiveKit(kitLabel, optionalBSPKitDto.get(), notificationUtil);
            KitInfo kitInfo = maybeKitInfo.get();
            ClinicalKitDto clinicalKit = new ClinicalKitDto();
            logger.info("Creating clinical kit to return to GP " + kitLabel);
            clinicalKit.setCollaboratorParticipantId(kitInfo.getCollaboratorParticipantId());
            clinicalKit.setSampleId(kitInfo.getCollaboratorSampleId());
            clinicalKit.setMaterialType(kitInfo.getMaterialInfo());
            clinicalKit.setVesselType(kitInfo.getReceptacleName());
            clinicalKit.setSampleType(kitInfo.getKitType());
            clinicalKit.setMfBarcode(kitLabel);
            clinicalKit.setSampleCollection(kitInfo.getSampleCollectionBarcode());
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(kitInfo.getRealm());
            clinicalKit.setNecessaryParticipantDataToClinicalKit(optionalBSPKitDto.get().getDdpParticipantId(), ddpInstance);
            return clinicalKit;
        }
    }

}
