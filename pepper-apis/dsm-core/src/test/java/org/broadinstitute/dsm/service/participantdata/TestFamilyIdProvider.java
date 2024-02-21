package org.broadinstitute.dsm.service.participantdata;

public class TestFamilyIdProvider implements  FamilyIdProvider {
    private final long familyId;

    public TestFamilyIdProvider(long familyId) {
        this.familyId = familyId;
    }

    @Override
    public long createFamilyId(String ddpParticipantId) {
        return familyId;
    }
}
