package org.broadinstitute.dsm.model.participant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantWrapperTest {
    public static final int PROXIES_QUANTITY = 5;
    private static ElasticSearchTest elasticSearchable;

    @BeforeClass
    public static void setUp() {
        elasticSearchable = new ElasticSearchTest();
    }


    @Test
    public void getParticipantIdFromElasticList() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, elasticSearchable);
        List<ElasticSearchParticipantDto> elasticSearchList = elasticSearchable.getParticipantsWithinRange("", 0, 50).getEsParticipants();
        List<String> participantIds = participantWrapper.getParticipantIdsFromElasticList(elasticSearchList);
        Assert.assertEquals(10, participantIds.size());
    }

    @Test
    public void getProxiesFromElasticList() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, elasticSearchable);
        List<ElasticSearchParticipantDto> elasticSearchList = elasticSearchable.getParticipantsWithinRange("", 0, 50).getEsParticipants();
        Map<String, List<String>> proxyIds = participantWrapper.getProxiesIdsFromElasticList(elasticSearchList);
        Assert.assertTrue(proxyIds.size() > 0);
        Assert.assertEquals(PROXIES_QUANTITY, proxyIds.values().stream().findFirst().get().size());
    }

    @Test
    public void getProxiesWithParticipantIdsByProxiesIds() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, elasticSearchable);
        List<ElasticSearchParticipantDto> elasticSearchList = elasticSearchable.getParticipantsWithinRange("", 0, 50).getEsParticipants();
        Map<String, List<String>> proxiesIdsFromElasticList = participantWrapper.getProxiesIdsFromElasticList(elasticSearchList);
        Map<String, List<ElasticSearchParticipantDto>> proxiesByParticipantIds = participantWrapper.getProxiesWithParticipantIdsByProxiesIds(
                "", proxiesIdsFromElasticList);
        Assert.assertEquals(proxiesByParticipantIds.keySet().size(), proxiesByParticipantIds.keySet().size());
        String parentId = proxiesIdsFromElasticList.keySet().stream().findFirst().get();
        String proxyId = proxiesIdsFromElasticList.values().stream().findFirst().get().get(0);
        Assert.assertEquals(proxiesByParticipantIds.get(parentId).get(0).getParticipantId(), proxyId);
    }

    @Test
    public void sortBySelfElseById() {
        Random random = new Random();
        String[] memberTypes = new String[] {"SISTER", "COUSIN", "SELF", "BROTHER"};
        AtomicInteger i = new AtomicInteger(0);
        List<ParticipantData> pDatas = Stream.generate(() -> new ParticipantData.Builder()
                .withData(String.format("{\"MEMTER_TYPE\":\"%s\"}", memberTypes[i.getAndIncrement()]))
                .withParticipantDataId(random.nextInt(100))
                .build()).limit(4).collect(Collectors.toList());
        ParticipantWrapper participantWrapper = new ParticipantWrapper(new ParticipantWrapperPayload.Builder().build(), elasticSearchable);
        participantWrapper.sortBySelfElseById(Collections.singleton(pDatas));
        Assert.assertTrue(pDatas.get(0).getData().orElse("").contains(FamilyMemberConstants.MEMBER_TYPE_SELF));
    }

    private static class ElasticSearchTest implements ElasticSearchable {

        ElasticSearch elasticSearch = new ElasticSearch();

        @Override
        public ElasticSearch getParticipantsWithinRange(String esParticipantsIndex, int from, int to) {
            List<ElasticSearchParticipantDto> result = Stream.generate(() -> {
                        ESProfile esProfile = new ESProfile();
                        esProfile.setGuid(randomGuidGenerator());
                        return new ElasticSearchParticipantDto.Builder()
                                .withProfile(esProfile)
                                .withProxies(generateProxies())
                                .build();
                    })
                    .limit(10)
                    .collect(Collectors.toList());
            elasticSearch.setEsParticipants(result);
            return elasticSearch;
        }

        @Override
        public ElasticSearch getParticipantsByIds(String esParticipantIndex, List<String> participantIds) {
            List<ElasticSearchParticipantDto> result = new ArrayList<>();
            participantIds.forEach(pId -> {
                ESProfile esProfile = new ESProfile();
                esProfile.setGuid(pId);
                result.add(
                        new ElasticSearchParticipantDto.Builder()
                                .withProfile(esProfile)
                                .build()
                );
            });
            elasticSearch.setEsParticipants(result);
            return elasticSearch;
        }

        @Override
        public long getParticipantsSize(String esParticipantsIndex) {
            return 0;
        }

        @Override
        public ElasticSearch getParticipantsByRangeAndFilter(String esParticipantsIndex, int from, int to, AbstractQueryBuilder queryBuilder) {
            return null;
        }

        @Override
        public ElasticSearch getParticipantsByRangeAndIds(String participantIndexES, int from, int to, List<String> participantIds) {
            return null;
        }

        @Override
        public ElasticSearchParticipantDto getParticipantById(String esParticipantsIndex, String id) {
            return null;
        }

        @Override
        public ElasticSearch getAllParticipantsDataByInstanceIndex(String esParticipantsIndex) {
            return null;
        }
    }


    @Test
    public void testGuidGenerator() {
        String guid = randomGuidGenerator();
        Assert.assertEquals(20, guid.length());
    }

    public static List<String> generateProxies() {
        return Stream
                .generate(ParticipantWrapperTest::randomGuidGenerator)
                .limit(PROXIES_QUANTITY)
                .collect(Collectors.toList());
    }

    public static String randomGuidGenerator() {
        char[] letters = new char[] {'A', 'B', 'C', 'D', 'F', 'G', 'H', 'I', 'G'};
        return generateParticipantId(letters, 20);
    }

    public static String randomLegacyAltPidGenerator() {
        char[] letters = new char[] {'a', 'B', 'C', 'd', 'F', 'g', 'H', 'I', 'j', 'K', 'L', 'm', 'X', 'Y', 'z'};
        return generateParticipantId(letters, 50);
    }

    private static String generateParticipantId(char[] letters, int stringSize) {
        StringBuilder guid = new StringBuilder();
        Random rand = new Random();
        for (int i = 1; i <= stringSize; i++) {
            if (i % 5 == 0) {
                guid.append(rand.nextInt(10));
            } else {
                guid.append(letters[rand.nextInt(letters.length)]);
            }
        }
        return guid.toString();
    }
}