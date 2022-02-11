package org.broadinstitute.dsm.db.dto.ddp.instance;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    String notificationRecipients;
    Boolean migratedDdp;
    String billingReference;
    String esParticipantIndex;
    String esActivityDefinitionIndex;
    String esUsersIndex;

    public List<String> getNotificationRecipients() {
        if (StringUtils.isBlank(notificationRecipients)) {
            return Collections.emptyList();
        }
        notificationRecipients = notificationRecipients.replaceAll("\\s", "");
        return Arrays.asList(notificationRecipients.split(","));
    }


    private DDPInstanceDto(Builder builder) {
        Class<DDPInstanceDto> ddpInstanceDtoClazz = DDPInstanceDto.class;
        Field[] declaredFields = ddpInstanceDtoClazz.getDeclaredFields();
        Map<String, Field> builderFieldsMap =
                Arrays.stream(builder.getClass().getDeclaredFields()).collect(Collectors.toMap(Field::getName, Function.identity()));
        for (Field field : declaredFields) {
            try {
                field.set(this, builderFieldsMap.get(field.getName()).get(builder));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static class Builder {
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
        String notificationRecipients;
        Boolean migratedDdp;
        String billingReference;
        String esParticipantIndex;
        String esActivityDefinitionIndex;
        String esUsersIndex;

        public Builder() {
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withInstanceName(String instanceName) {
            this.instanceName = instanceName;
            return this;
        }

        public Builder withStudyGuid(String studyGuid) {
            this.studyGuid = studyGuid;
            return this;
        }

        public Builder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder withBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder withIsActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder withBspGroup(String bspGroup) {
            this.bspGroup = bspGroup;
            return this;
        }

        public Builder withBspCollection(String bspCollection) {
            this.bspCollection = bspCollection;
            return this;
        }

        public Builder withBspOrganims(String bspOrganism) {
            this.bspOrganism = bspOrganism;
            return this;
        }

        public Builder withCollaboratorIdPrefix(String collaboratorIdPrefix) {
            this.collaboratorIdPrefix = collaboratorIdPrefix;
            return this;
        }

        public Builder withReminderNotificationWks(int reminderNotificationWks) {
            this.reminderNotificationWks = reminderNotificationWks;
            return this;
        }

        public Builder withMrAttentionFlagD(int mrAttentionFlagD) {
            this.mrAttentionFlagD = mrAttentionFlagD;
            return this;
        }

        public Builder withTissueAttentionFlagD(int tissueAttentionFlagD) {
            this.tissueAttentionFlagD = tissueAttentionFlagD;
            return this;
        }

        public Builder withAuth0Token(boolean auth0Token) {
            this.auth0Token = auth0Token;
            return this;
        }

        public Builder withNotificationRecipient(String notificationRecipients) {
            this.notificationRecipients = notificationRecipients;
            return this;
        }

        public Builder withMigratedDdp(boolean migratedDdp) {
            this.migratedDdp = migratedDdp;
            return this;
        }

        public Builder withBillingReference(String billingReference) {
            this.billingReference = billingReference;
            return this;
        }

        public Builder withEsParticipantIndex(String esParticipantIndex) {
            this.esParticipantIndex = esParticipantIndex;
            return this;
        }

        public Builder withEsActivityDefinitionIndex(String esActivityDefinitionIndex) {
            this.esActivityDefinitionIndex = esActivityDefinitionIndex;
            return this;
        }

        public Builder withEsUsersIndex(String esUsersIndex) {
            this.esUsersIndex = esUsersIndex;
            return this;
        }

        public DDPInstanceDto build() {
            return new DDPInstanceDto(this);
        }

    }

}
