package org.broadinstitute.dsm.pubsub.study.osteo;

import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class OsteoWorkflowStatusUpdateTest {


    @BeforeClass
    public static void setUp() {
        TestHelper.setupDB();
    }

    @Test
    @Ignore
    public void update() {

        int oldOsteoInstanceId = 11;
        String oldOsteoInstanceName = "Osteo";
        String oldOsteoESIndex = "participants_structured.cmi.cmi-osteo";

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .withDdpInstanceId(oldOsteoInstanceId)
                .withInstanceName(oldOsteoInstanceName)
                .withEsParticipantIndex(oldOsteoESIndex)
                .build();

        String oldOsteoParticipantGuid = "artificial guid";

        OsteoWorkflowStatusUpdate osteoWorkflowStatusUpdate = OsteoWorkflowStatusUpdate.of(ddpInstanceDto, oldOsteoParticipantGuid);
        osteoWorkflowStatusUpdate.update();

    }

}
