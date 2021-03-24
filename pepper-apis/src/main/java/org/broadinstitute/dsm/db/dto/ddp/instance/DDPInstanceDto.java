package org.broadinstitute.dsm.db.dto.ddp.instance;

import java.sql.Types;

import lombok.Data;
import lombok.NonNull;

@Data
public class DDPInstanceDto {

    Integer ddpInstanceId;
    String instanceName;
    String studyGuid;
    String displayName;
    String baseUrl;
    Boolean isActive;
    String bspGroup;
    String bspCollection;
    String bspOrganism;
    String collaboratorIdPrefix;
    Integer reminderNotificationWks;
    Integer mrAttentionFlagD;
    Integer tissueAttentionFlagD;
    Boolean auth0Token;
    String notificiationRecipients;
    Boolean migratedDdp;
    String billingReference;
    String esParticipantIndex;
    String esActivityDefinitionIndex;
    String esUsersIndex;


    public DDPInstanceDto(String instanceName, String studyGuid, String displayName, String baseUrl, @NonNull Boolean isActive,
                          String bspGroup, String bspCollection, String bspOrganism, String collaboratorIdPrefix,
                          Integer reminderNotificationWks,
                          Integer mrAttentionFlagD, Integer tissueAttentionFlagD, @NonNull Boolean auth0Token, String notificiationRecipients,
                          @NonNull Boolean migratedDdp, String billingReference, String esParticipantIndex, String esActivityDefinitionIndex,
                          String esUsersIndex) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.isActive = isActive;
        this.bspGroup = bspGroup;
        this.bspCollection = bspCollection;
        this.bspOrganism = bspOrganism;
        this.collaboratorIdPrefix = collaboratorIdPrefix;
        this.reminderNotificationWks = reminderNotificationWks;
        this.mrAttentionFlagD = mrAttentionFlagD;
        this.tissueAttentionFlagD = tissueAttentionFlagD;
        this.auth0Token = auth0Token;
        this.notificiationRecipients = notificiationRecipients;
        this.migratedDdp = migratedDdp;
        this.billingReference = billingReference;
        this.esParticipantIndex = esParticipantIndex;
        this.esActivityDefinitionIndex = esActivityDefinitionIndex;
        this.esUsersIndex = esUsersIndex;
    }

    public static DDPInstanceDto of(boolean isActive, boolean auth0Token, boolean migratedDdp) {
        return new DDPInstanceDto(
                null,
                null,
                null,
                null,
                isActive,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                auth0Token,
                null,
                migratedDdp,
                null,
                null,
                null,
                null
        );
    }
}
