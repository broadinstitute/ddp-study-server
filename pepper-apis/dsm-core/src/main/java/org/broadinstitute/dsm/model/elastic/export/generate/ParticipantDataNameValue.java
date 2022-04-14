package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.NameValue;

public class ParticipantDataNameValue extends NameValue {

    private Integer ddpInstanceId;
    private String ddpParticipantId;
    private String fieldTypeId;

    public ParticipantDataNameValue(String name, Object value, Integer ddpInstanceId, String ddpParticipantId, String fieldTypeId) {
        super(name, value);
        this.ddpInstanceId = ddpInstanceId;
        this.ddpParticipantId = ddpParticipantId;
        this.fieldTypeId = fieldTypeId;
    }

}
