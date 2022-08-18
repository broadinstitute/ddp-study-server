
package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDaoImpl;
import org.broadinstitute.dsm.model.patch.process.ParentRelatedPatchPreProcessor;
import org.junit.Assert;
import org.junit.Test;

public class ParentRelatedPatchPreProcessorTest {

    @Test
    public void parentIsDdpParticipantId() {
        var patch          = new Patch("ddpParticipantId", "artificial_guid");
        var preProcessor   = new ParentRelatedPatchPreProcessorMock(ParticipantDaoMock.fromParticipantId(12345));
        var processedPatch = preProcessor.process(patch);
        Assert.assertEquals(processedPatch, new Patch("participantId", "12345"));
    }

    @Test
    public void parentIsParticipantId() {
        var patch            = new Patch("participantId", "77777");
        var preProcessor     = new ParentRelatedPatchPreProcessorMock(ParticipantDaoMock.fromParticipantId(0));
        var processedPatch = preProcessor.process(patch);
        Assert.assertEquals(processedPatch, new Patch("participantId", "77777"));
    }

    private static class ParentRelatedPatchPreProcessorMock extends ParentRelatedPatchPreProcessor {

        public ParentRelatedPatchPreProcessorMock(ParticipantDao participantDao) {
            super(participantDao);
        }

        @Override
        int getDdpInstanceIdAsInt(String realm) {
            return 0;
        }
    }

    private static class ParticipantDaoMock extends ParticipantDaoImpl {

        int participantId;

        private ParticipantDaoMock(int participantId) {
            this.participantId = participantId;
        }

        public static ParticipantDaoMock fromParticipantId(int participantId) {
            return new ParticipantDaoMock(participantId);
        }

        @Override
        public Optional<Integer> getParticipantIdByGuidAndDdpInstanceId(String guid, int ddpInstanceId) {
            return Optional.of(12345);
        }
    }
}