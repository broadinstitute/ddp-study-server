
package org.broadinstitute.dsm.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewOsteoParticipant extends Participant {

    public NewOsteoParticipant(long participantId, String ddpParticipantId, int ddpInstanceId) {
        super(participantId, ddpParticipantId, ddpInstanceId);
    }
}
