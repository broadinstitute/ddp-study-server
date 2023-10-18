package org.broadinstitute.dsm.model.elastic.search;


import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.dsm.model.elastic.Address;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperTest;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.search.SearchHit;
import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchTest {

    private static final Gson GSON = new Gson();

    private static Profile esProfileGeneratorWithGuid() {
        Profile esProfile = new Profile();
        esProfile.setGuid(ParticipantWrapperTest.randomGuidGenerator());
        return esProfile;
    }

    private static Profile esProfileGeneratorWithLegacyAltPid() {
        Profile esProfile = new Profile();
        esProfile.setLegacyAltPid(ParticipantWrapperTest.randomLegacyAltPidGenerator());
        return esProfile;
    }

    @Test
    public void getParticipantIdFromProfile() {
        Profile profile = esProfileGeneratorWithGuid();
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder().withProfile(profile).build();
        String participantId = elasticSearchParticipantDto.getParticipantId();
        Assert.assertEquals(profile.getGuid(), participantId);
    }

    @Test
    public void getParticipantIdFromProfileIfGuidEmpty() {
        Profile esProfileWithLegacyAltPid = esProfileGeneratorWithLegacyAltPid();
        ElasticSearchParticipantDto elasticSearchParticipantDto =
                new ElasticSearchParticipantDto.Builder().withProfile(esProfileWithLegacyAltPid).build();
        String participantId = elasticSearchParticipantDto.getParticipantId();
        Assert.assertEquals(esProfileWithLegacyAltPid.getLegacyAltPid(), participantId);
    }

    @Test
    public void getParticipantIdFromProfileIfEmpty() {
        Profile esProfile = new Profile();
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder().build();
        String participantId = elasticSearchParticipantDto.getParticipantId();
        Assert.assertEquals("", participantId);
    }

    @Test
    public void getDdpFromIndex() {
        String index = "participants_structured.rgp.rgp";
        ElasticSearch elasticSearch = new ElasticSearch();
        String ddpFromIndex = elasticSearch.getDdpFromIndex(index);
        Assert.assertEquals("rgp", ddpFromIndex);
    }

    @Test
    public void getDdpFromIndexIfEmptyOrNull() {
        ElasticSearch elasticSearch = new ElasticSearch();
        String ddpFromIndexIfNull = elasticSearch.getDdpFromIndex(null);
        String ddpFromIndexIfEmpty = elasticSearch.getDdpFromIndex("");
        Assert.assertEquals("", ddpFromIndexIfNull);
        Assert.assertEquals("", ddpFromIndexIfEmpty);
    }

    @Test
    public void parseSourceMap() {
        Profile esProfile = new Profile();
        esProfile.setFirstName("Tommy");
        Address esAddress = new Address();
        esAddress.setCountry("Barsum");
        ElasticSearchParticipantDto elasticSearchParticipantDto =
                new ElasticSearchParticipantDto.Builder().withStatusTimeStamp(1_000_000L).withProfile(esProfile).withStatus("TESTING")
                        .withAddress(esAddress).build();
        Map<String, Object> esMap = GSON.fromJson(GSON.toJson(elasticSearchParticipantDto), Map.class);
        ElasticSearchParticipantDto esParticipantDto = new ElasticSearch().parseSourceMap(esMap);
        try {
            Assert.assertEquals("Tommy", esParticipantDto.getProfile().map(Profile::getFirstName).orElse(""));
            Assert.assertEquals("Barsum", esParticipantDto.getAddress().map(Address::getCountry).orElse(""));
            Assert.assertEquals("TESTING", esParticipantDto.getStatus().orElse(""));
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void extractLegacyAltPidGuidPair() {
        ElasticSearch elasticSearch = new ElasticSearch();
        class MockSearchHitProxy extends SearchHitProxy {
            private String legacyAltPid;
            private List<String> proxies;
            private String guid;

            public MockSearchHitProxy(SearchHit searchHit, String guid, String legacyAltPid, List<String> proxies) {
                super(searchHit);
                this.guid = guid;
                this.proxies = proxies;
                this.legacyAltPid = legacyAltPid;
            }

            @Override
            Map<String, Object> getSourceAsMap() {
                Map<String, String> profile = Map.of(ElasticSearchUtil.LEGACY_ALT_PID, legacyAltPid, ElasticSearchUtil.GUID, guid);
                return Map.of(ElasticSearchUtil.PROFILE, profile, ElasticSearchUtil.PROXIES, proxies);
            }
        }

        String parentGuid = "TEST1234567891011123";
        String childGuid = "TEST1234567891011124";
        String legacyAltPid = "283hdsjd92j32njsjdbakdj283ndjdadsj2n3n13j";
        String aloneLegacyAltPid = "283hdsjd92j32njsjdbakdj283ndjdadsj2n3n13k";
        String aloneGuid = "TEST1234567891011125";
        MockSearchHitProxy mockSearchHitProxy = new MockSearchHitProxy(null, parentGuid, legacyAltPid, Collections.emptyList());
        MockSearchHitProxy mockSearchHitProxy2 = new MockSearchHitProxy(null, childGuid, legacyAltPid, List.of(parentGuid));
        MockSearchHitProxy mockSearchHitProxy4 = new MockSearchHitProxy(null, aloneGuid, aloneLegacyAltPid, Collections.emptyList());
        Map<String, String> guidsByLegacyAltPid = elasticSearch.extractLegacyAltPidGuidPair(
                new MockSearchHitProxy[] {mockSearchHitProxy, mockSearchHitProxy2, mockSearchHitProxy4});
        Assert.assertEquals(childGuid, guidsByLegacyAltPid.get(legacyAltPid));
        Assert.assertEquals(aloneGuid, guidsByLegacyAltPid.get(aloneLegacyAltPid));
    }


}
