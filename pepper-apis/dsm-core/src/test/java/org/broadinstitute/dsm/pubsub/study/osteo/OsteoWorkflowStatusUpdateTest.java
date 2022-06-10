package org.broadinstitute.dsm.pubsub.study.osteo;

import org.broadinstitute.dsm.db.DDPInstance;
import org.junit.Ignore;
import org.junit.Test;

public class OsteoWorkflowStatusUpdateTest {

    @Test
    @Ignore
    public void update() {

        String osteo2DdpInstanceId = "27";
        String osteo2InstanceName = "osteo2";
        String osteo2ParticipantESIndex = "participants_structured.cmi.cmi-osteo";

        DDPInstance ddpInstance = new DDPInstance(osteo2DdpInstanceId, osteo2InstanceName, null, null, false,
                0, 0, false, null,
                false, null, osteo2ParticipantESIndex, null, null, null);

        String osteo2DdpParticipantId = "guid";

        OsteoWorkflowStatusUpdate osteoWorkflowStatusUpdate = OsteoWorkflowStatusUpdate.of(ddpInstance, osteo2DdpParticipantId);
        osteoWorkflowStatusUpdate.update();

    }

}