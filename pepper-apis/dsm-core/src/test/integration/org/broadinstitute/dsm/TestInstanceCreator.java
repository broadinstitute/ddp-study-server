package org.broadinstitute.dsm;

import java.util.Objects;

import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

public class TestInstanceCreator {

    public static final String TEST_INSTANCE = "TestInstance";

    private static final String ES_PARTICIPANT_INDEX = "participants_structured.cmi.angio";
    private DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private Integer newlyCreatedInstanceId;

    public TestInstanceCreator() {
        TestHelper.setupDB();
    }

    public Integer create() {
        DDPInstanceDto instanceDto = new DDPInstanceDto.Builder()
                .withIsActive(false)
                .withAuth0Token(false)
                .withMigratedDdp(false)
                .withInstanceName(TestInstanceCreator.TEST_INSTANCE)
                .withEsParticipantIndex(ES_PARTICIPANT_INDEX)
                .build();
        newlyCreatedInstanceId = ddpInstanceDao.create(instanceDto);
        return newlyCreatedInstanceId;
    }

    public void delete() {
        if (Objects.nonNull(newlyCreatedInstanceId)) {
            ddpInstanceDao.delete(newlyCreatedInstanceId);
        }
    }

    public Integer getDdpInstanceId() {
        return Objects.requireNonNull(newlyCreatedInstanceId);
    }


}
