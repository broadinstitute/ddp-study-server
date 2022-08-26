package org.broadinstitute.dsm.util.export;

@Data
    public static class ParticipantExportPayload {

        private final long participantId;
        private final String ddpParticipantId;
        private final String instanceId;
        private final String instanceName;
        private final DDPInstanceDto ddpInstanceDto;

        public ParticipantExportPayload(long participantId, String ddpParticipantId, String instanceId, String instanceName,
                                        DDPInstanceDto ddpInstanceDto) {
            this.participantId = participantId;
            this.ddpParticipantId = ddpParticipantId;
            this.instanceId = instanceId;
            this.instanceName = instanceName;
            this.ddpInstanceDto = ddpInstanceDto;
        }
    }