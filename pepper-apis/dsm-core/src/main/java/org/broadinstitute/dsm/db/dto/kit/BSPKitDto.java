package org.broadinstitute.dsm.db.dto.kit;

import lombok.Getter;

@Getter
public class BSPKitDto {
    private final String baseUrl;
    private final String bspSampleId;
    private final String bspParticipantId;
    private final String instanceName;
    private final String bspOrganism;
    private final String bspCollection;
    private final String ddpParticipantId;
    private String bspMaterialType;
    private String bspReceptacleType;
    private boolean hasParticipantNotifications;
    private String participantExitId;
    private String deactivationDate;
    private String notificationRecipient;
    private String kitTypeName;

    public BSPKitDto(String instanceName,
                     String baseUrl,
                     String bspSampleId,
                     String bspParticipantId,
                     String bspOrganism,
                     String bspCollection,
                     String ddpParticipantId,
                     String bspMaterialType,
                     String bspReceptacleType,
                     boolean hasParticipantNotifications,
                     String participantExitId,
                     String deactivationDate,
                     String notificationRecipient,
                     String kitTypeName) {

        this.instanceName = instanceName;
        this.baseUrl = baseUrl;
        this.bspSampleId = bspSampleId;
        this.bspParticipantId = bspParticipantId;
        this.bspOrganism = bspOrganism;
        this.bspCollection = bspCollection;
        this.ddpParticipantId = ddpParticipantId;
        this.bspMaterialType = bspMaterialType;
        this.bspReceptacleType = bspReceptacleType;
        this.hasParticipantNotifications = hasParticipantNotifications;
        this.participantExitId = participantExitId;
        this.deactivationDate = deactivationDate;
        this.notificationRecipient = notificationRecipient;
        this.kitTypeName = kitTypeName;
    }
}
