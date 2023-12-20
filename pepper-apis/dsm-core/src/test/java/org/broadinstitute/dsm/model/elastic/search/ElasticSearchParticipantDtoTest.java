package org.broadinstitute.dsm.model.elastic.search;

import org.broadinstitute.dsm.model.elastic.Profile;
import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchParticipantDtoTest {

    private static final String QUERIED_PARTICIPANT_ID = "123FOUR56";

    private static final String PARTICIPANT_GUID = "GUID123";

    private static final String ALTPID = "ALTPID123";

    @Test
    public void testGetQueriedParticipantId() {
        var dto = new ElasticSearchParticipantDto();
        dto.setQueriedParticipantId(QUERIED_PARTICIPANT_ID);
        Assert.assertEquals(QUERIED_PARTICIPANT_ID, dto.getQueriedParticipantId());
    }

    @Test
    public void testGetParticipantIdReturnsGUIDIdWhenProfileIsSet() {
        var dto = new ElasticSearchParticipantDto();
        var profile = new Profile();
        profile.setGuid(PARTICIPANT_GUID);
        dto.setProfile(profile);
        Assert.assertEquals(PARTICIPANT_GUID, dto.getParticipantId());
    }

    @Test
    public void testGetParticipantIdReturnsLegacyIdIdWhenProfileOnlyHasAltPid() {
        var dto = new ElasticSearchParticipantDto();
        var profile = new Profile();
        profile.setLegacyAltPid(ALTPID);
        dto.setProfile(profile);
        Assert.assertEquals(ALTPID, dto.getParticipantId());
    }

    @Test
    public void testGetParticipantIdReturnsGUIDIdIdWhenProfileHasAltPidAndGuid() {
        var dto = new ElasticSearchParticipantDto();
        var profile = new Profile();
        profile.setLegacyAltPid(ALTPID);
        profile.setGuid(PARTICIPANT_GUID);
        dto.setProfile(profile);
        Assert.assertEquals(PARTICIPANT_GUID, dto.getParticipantId());
    }
}
