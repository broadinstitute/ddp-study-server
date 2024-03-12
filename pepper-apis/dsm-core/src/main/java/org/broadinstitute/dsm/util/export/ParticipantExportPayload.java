
package org.broadinstitute.dsm.util.export;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

@Data
@AllArgsConstructor
public class ParticipantExportPayload {

    private final int participantId;
    private final String ddpParticipantId;
    private final int instanceId;
    private final String instanceName;
    private final DDPInstanceDto ddpInstanceDto;
}
