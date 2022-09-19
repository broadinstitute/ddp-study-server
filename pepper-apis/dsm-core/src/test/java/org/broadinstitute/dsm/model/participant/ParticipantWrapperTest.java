package org.broadinstitute.dsm.model.participant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchable;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ParticipantWrapperTest {
    public static final int PROXIES_QUANTITY = 5;
    private ElasticSearchTest elasticSearchable;

    @Before
    public void setUp() {
        elasticSearchable = new ElasticSearchTest();
    }

    public static List<String> generateProxies() {
        return Stream.generate(ParticipantWrapperTest::randomGuidGenerator).limit(PROXIES_QUANTITY).collect(Collectors.toList());
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

    @Test
    public void getParticipantIdFromElasticList() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder().build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, elasticSearchable);
        List<ElasticSearchParticipantDto> elasticSearchList = elasticSearchable.getParticipantsWithinRange("", 0, 50).getEsParticipants();
        List<String> participantIds = participantWrapper.getParticipantIdsFromElasticList(elasticSearchList);
        Assert.assertEquals(10, participantIds.size());
    }

    @Test
    public void getProxiesFromElasticList() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder().build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, elasticSearchable);
        List<ElasticSearchParticipantDto> elasticSearchList = elasticSearchable.getParticipantsWithinRange("", 0, 50).getEsParticipants();
        Map<String, List<String>> proxyIds = participantWrapper.getProxiesIdsFromElasticList(elasticSearchList);
        Assert.assertTrue(proxyIds.size() > 0);
        Assert.assertEquals(PROXIES_QUANTITY, proxyIds.values().stream().findFirst().get().size());
    }

    @Test
    public void sortBySelfElseById() {
        Random random = new Random();
        String[] memberTypes = new String[] {"SISTER", "SELF", "COUSIN", "BROTHER"};
        AtomicInteger i = new AtomicInteger(0);
        List<ParticipantData> participantDataList = Stream.generate(
                () -> new ParticipantData.Builder().withData(String.format("{\"MEMBER_TYPE\":\"%s\"}", memberTypes[i.getAndIncrement()]))
                        .withParticipantDataId(random.nextInt(100)).build()).limit(4).collect(Collectors.toList());
        ParticipantWrapper participantWrapper =
                new ParticipantWrapper(new ParticipantWrapperPayload.Builder().build(), new ElasticSearchTest());
        participantWrapper.sortBySelfElseById(participantDataList);
        Assert.assertTrue(participantDataList.get(0).getData().orElse("").contains(FamilyMemberConstants.MEMBER_TYPE_SELF));
    }

    @Test
    public void testGuidGenerator() {
        String guid = randomGuidGenerator();
        Assert.assertEquals(20, guid.length());
    }

    @Test
    public void fillParticipantWrapperDtosWithProxies() {

        ParticipantWrapperPayload payload =
                new ParticipantWrapperPayload.Builder().withDdpInstanceDto(new DDPInstanceDto.Builder().build()).build();
        ParticipantWrapper participantWrapper = new ParticipantWrapper(payload, new ElasticSearchTest());
        ParticipantWrapperDto participantWrapperDto1 = new ParticipantWrapperDto();
        List<String> proxies1 = Arrays.asList("A1", "A2");
        ElasticSearchParticipantDto elasticSearchParticipantDto1 = new ElasticSearchParticipantDto.Builder().withProxies(proxies1).build();
        participantWrapperDto1.setEsData(elasticSearchParticipantDto1);
        ParticipantWrapperDto participantWrapperDto2 = new ParticipantWrapperDto();
        List<String> proxies2 = List.of("B1");
        ElasticSearchParticipantDto elasticSearchParticipantDto2 = new ElasticSearchParticipantDto.Builder().withProxies(proxies2).build();
        participantWrapperDto2.setEsData(elasticSearchParticipantDto2);
        List<ParticipantWrapperDto> participantWrapperDtos = Arrays.asList(participantWrapperDto1, participantWrapperDto2);

        Assert.assertNull(participantWrapperDto1.getProxyData());
        Assert.assertNull(participantWrapperDto2.getProxyData());

        participantWrapper.fillParticipantWrapperDtosWithProxies(participantWrapperDtos,
                Stream.concat(proxies1.stream(), proxies2.stream()).collect(Collectors.toList()));

        Assert.assertEquals("B1", participantWrapperDto2.getProxyData().get(0).getParticipantId());
        Assert.assertEquals(proxies1, participantWrapperDto1.getProxyData().stream().map(ElasticSearchParticipantDto::getParticipantId)
                .collect(Collectors.toList()));
    }

    @Test
    public void prepareQuery() {
        ParticipantWrapperPayload participantWrapperPayload = new ParticipantWrapperPayload.Builder()
                .withDdpInstanceDto(new DDPInstanceDto.Builder().build())
                .build();

        ParticipantWrapper participantWrapper = new ParticipantWrapper(participantWrapperPayload, new ElasticSearchTest());
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("t", " AND ( t.tissue_type = 'block' OR t.tissue_type = 'slide' OR t.tissue_type = 'scrolls' ) ");
        filters.put("ES", " AND dsm.diagnosisYear = 2014");
        AbstractQueryBuilder<?> actualQuery = participantWrapper.prepareQuery(filters);

        BoolQueryBuilder expectedQuery = new BoolQueryBuilder();

        BoolQueryBuilder tissueQuery = new BoolQueryBuilder();
        BoolQueryBuilder orQuery = new BoolQueryBuilder();
        orQuery.should(new MatchQueryBuilder("dsm.tissue.tissueType", "block"));
        orQuery.should(new MatchQueryBuilder("dsm.tissue.tissueType", "slide"));
        orQuery.should(new MatchQueryBuilder("dsm.tissue.tissueType", "scrolls"));
        tissueQuery.must(new NestedQueryBuilder("dsm.tissue", orQuery, ScoreMode.Avg));

        BoolQueryBuilder esQuery = new BoolQueryBuilder();
        esQuery.must(new MatchQueryBuilder("dsm.diagnosisYear", 2014L).operator(Operator.AND));

        expectedQuery.must(tissueQuery);
        expectedQuery.must(esQuery);

        Assert.assertEquals(expectedQuery, actualQuery);
    }

    private static class ElasticSearchTest implements ElasticSearchable {

        ElasticSearch elasticSearch = new ElasticSearch();

        @Override
        public ElasticSearch getParticipantsWithinRange(String esParticipantsIndex, int from, int to) {
            List<ElasticSearchParticipantDto> result = Stream.generate(() -> {
                Profile esProfile = new Profile();
                esProfile.setGuid(randomGuidGenerator());
                return new ElasticSearchParticipantDto.Builder().withProfile(esProfile).withProxies(generateProxies()).build();
            }).limit(10).collect(Collectors.toList());
            elasticSearch.setEsParticipants(result);
            return elasticSearch;
        }

        @Override
        public ElasticSearch getParticipantsByIds(String esParticipantIndex, List<String> participantIds) {
            List<ElasticSearchParticipantDto> result = new ArrayList<>();
            participantIds.forEach(pId -> {
                Profile esProfile = new Profile();
                esProfile.setGuid(pId);
                result.add(new ElasticSearchParticipantDto.Builder().withProfile(esProfile).build());
            });
            elasticSearch.setEsParticipants(result);
            return elasticSearch;
        }

        @Override
        public long getParticipantsSize(String esParticipantsIndex) {
            return 0;
        }

        @Override
        public ElasticSearch getParticipantsByRangeAndFilter(String esParticipantsIndex, int from, int to,
                                                             AbstractQueryBuilder queryBuilder) {
            return null;
        }

        @Override
        public ElasticSearch getParticipantsByRangeAndFilter(String esParticipantsIndex, int from, int to,
                                                             AbstractQueryBuilder queryBuilder, String instanceName) {
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

        @Override
        public Map<String, Map<String, Object>> getActivityDefinitions(DDPInstance ddpInstance) {
            return Map.of();
        }
    }
}
