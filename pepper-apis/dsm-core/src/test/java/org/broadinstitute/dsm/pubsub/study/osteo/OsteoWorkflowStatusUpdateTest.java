package org.broadinstitute.dsm.pubsub.study.osteo;

import org.broadinstitute.dsm.db.DDPInstance;
import org.junit.Ignore;
import org.junit.Test;

public class OsteoWorkflowStatusUpdateTest {

    @Test
    @Ignore
    public void update() {

        String oldOsteoInstanceId = "35";
        String oldOsteoInstanceName = "Osteo";
        String oldOsteoESIndex = "participants_structured.cmi.cmi-osteo";

        DDPInstance ddpInstance = new DDPInstance(oldOsteoInstanceId, oldOsteoInstanceName, null, null, false,
                0, 0, false, null,
                false, null, oldOsteoESIndex, null, null, null);

        String osteo2DdpParticipantId = "guid";

        OsteoWorkflowStatusUpdate osteoWorkflowStatusUpdate = OsteoWorkflowStatusUpdate.of(ddpInstance, osteo2DdpParticipantId);
        osteoWorkflowStatusUpdate.update();

    }

}