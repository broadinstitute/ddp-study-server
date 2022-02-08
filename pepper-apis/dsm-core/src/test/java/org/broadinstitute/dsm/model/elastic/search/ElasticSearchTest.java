package org.broadinstitute.dsm.model.elastic.search;


import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import org.broadinstitute.dsm.model.elastic.ESAddress;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperTest;
import org.junit.Assert;
import org.junit.Test;

public class ElasticSearchTest {

    private static final Gson GSON = new Gson();

    private static ESProfile esProfileGeneratorWithGuid() {
        ESProfile esProfile = new ESProfile();
        esProfile.setParticipantGuid(ParticipantWrapperTest.randomGuidGenerator());
        return esProfile;
    }

    private static ESProfile esProfileGeneratorWithLegacyAltPid() {
        ESProfile esProfile = new ESProfile();
        esProfile.setParticipantLegacyAltPid(ParticipantWrapperTest.randomLegacyAltPidGenerator());
        return esProfile;
    }

    @Test
    public void getParticipantIdFromProfile() {
        ESProfile profile = esProfileGeneratorWithGuid();
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .withProfile(profile)
                .build();
        String participantId = elasticSearchParticipantDto.getParticipantId();
        Assert.assertEquals(profile.getParticipantGuid(), participantId);
    }

    @Test
    public void getParticipantIdFromProfileIfGuidEmpty() {
        ESProfile esProfileWithLegacyAltPid = esProfileGeneratorWithLegacyAltPid();
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .withProfile(esProfileWithLegacyAltPid)
                .build();
        String participantId = elasticSearchParticipantDto.getParticipantId();
        Assert.assertEquals(esProfileWithLegacyAltPid.getParticipantLegacyAltPid(), participantId);
    }

    @Test
    public void getParticipantIdFromProfileIfEmpty() {
        ESProfile esProfile = new ESProfile();
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .build();
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
        ESProfile esProfile = new ESProfile();
        esProfile.setFirstName("Tommy");
        ESAddress esAddress = new ESAddress();
        esAddress.setCountry("Barsum");
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .withStatusTimeStamp(1_000_000L)
                .withProfile(esProfile)
                .withStatus("TESTING")
                .withAddress(esAddress)
                .build();
        Map<String, Object> esMap = GSON.fromJson(GSON.toJson(elasticSearchParticipantDto), Map.class);
        Optional<ElasticSearchParticipantDto> maybeElasticSearchParticipantDto = ElasticSearch.parseSourceMap(esMap);
        try {
            ElasticSearchParticipantDto esParticipantDto = maybeElasticSearchParticipantDto.get();
            Assert.assertEquals("Tommy", esParticipantDto.getProfile().map(ESProfile::getFirstName).orElse(""));
            Assert.assertEquals("Barsum", esParticipantDto.getAddress().map(ESAddress::getCountry).orElse(""));
            Assert.assertEquals("TESTING", esParticipantDto.getStatus().orElse(""));
        } catch (Exception e) {
            Assert.fail();
        }
    }


}
