package org.broadinstitute.dsm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ElasticSearchTest extends TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(RouteTestSample.class);

    @BeforeClass
    public static void before() throws Exception {
        setupDB();
    }

    @Test
    public void allParticipants() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-mbc");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.rgp.rgp");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void activityDefinitionSearchRequest() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("activity_definition.cmi.cmi-mbc");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingActivityDefinitionHits(response, esData);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void userSearchRequest() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("users.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingActivityDefinitionHits(response, esData);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchUserByGUID() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("users.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.wildcardQuery("profile.guid", "AVDDXUI451UXG3G7CAM9"));

                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "users.cmi.cmi-osteo");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByProfileDataLike() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.wildcardQuery("profile.firstName", "kiara*"));

                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-osteo");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByActivityDte() throws Exception {
        activityAnswer("participants_structured.cmi.cmi-brain", "CONSENT", "CONSENT_BLOOD", "true");
    }

    @Test
    public void searchPTByActivityAnswer() throws Exception {
        activityAnswer("participants_structured.cmi.cmi-brain", "PREQUAL", "PREQUAL_SELF_DESCRIBE", "DIAGNOSED");
    }

    public Map<String, Map<String, Object>> activityAnswer(String index, String activityCode, String stableId, String answer) throws Exception {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                activityAnswer.must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", stableId));
                activityAnswer.must(QueryBuilders.matchQuery("activities.questionsAnswers.answer", answer));
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities.questionsAnswers", activityAnswer, ScoreMode.Avg);

                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                queryBuilder.must(QueryBuilders.matchQuery("activities.activityCode", activityCode).operator(Operator.AND));
                queryBuilder.must(queryActivityAnswer);
                NestedQueryBuilder query = QueryBuilders.nestedQuery("activities", queryBuilder, ScoreMode.Avg);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", index);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
        return esData;
    }

    @Test
    public void searchPTByORActivityAnswers() throws Exception {
        compareSearches("participants_structured.cmi.angio", "ANGIOABOUTYOU", "COUNTRY", "US", "CA");
    }

    @Test
    public void searchPTByORActivityAnswers3() throws Exception {
        compareSearches("participants_structured.cmi.angio", "PREQUAL", "PREQUAL_SELF_DESCRIBE", "MAILING_LIST", "LOVED_ONE");
    }

    public void compareSearches(String index, String activityCode, String stableId, String answer1, String answer2) throws Exception {
        Map<String, Map<String, Object>> or = orActivityAnswers(index, activityCode, stableId, answer1, answer2);
        Map<String, Map<String, Object>> answerMap1 = activityAnswer(index, activityCode, stableId, answer1);
        Map<String, Map<String, Object>> answerMap2 = activityAnswer(index, activityCode, stableId, answer2);
        Map<String, Map<String, Object>> orCopy = new HashMap<>(or);
        Map<String, Map<String, Object>> answerMap1Copy = new HashMap<>(answerMap1);
        Map<String, Map<String, Object>> answerMap2Copy = new HashMap<>(answerMap2);

        answerMap1.forEach((key, value) -> {
            if (orCopy.containsKey(key)) {
                orCopy.remove(key);
            }
        });
        answerMap2.forEach((key, value) -> {
            if (orCopy.containsKey(key)) {
                orCopy.remove(key);
            }
        });

        or.forEach((key, value) -> {
            if (answerMap1Copy.containsKey(key)) {
                answerMap1Copy.remove(key);
            }
        });

        or.forEach((key, value) -> {
            if (answerMap2Copy.containsKey(key)) {
                answerMap2Copy.remove(key);
            }
        });

        Assert.assertEquals(0, orCopy.size());
        Assert.assertEquals((answerMap1.size() + answerMap2.size()), or.size());
    }

    public Map<String, Map<String, Object>> orActivityAnswers(String index, String activityCode, String stableId, String answer1, String answer2) throws Exception {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                activityAnswer.must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", stableId));
                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                orAnswers.should(QueryBuilders.matchQuery("activities.questionsAnswers.answer", answer1));
                orAnswers.should(QueryBuilders.matchQuery("activities.questionsAnswers.answer", answer2));
                activityAnswer.must(orAnswers);
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities.questionsAnswers", activityAnswer, ScoreMode.Avg);

                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                queryBuilder.must(QueryBuilders.matchQuery("activities.activityCode", activityCode).operator(Operator.AND));
                queryBuilder.must(queryActivityAnswer);
                NestedQueryBuilder query = QueryBuilders.nestedQuery("activities", queryBuilder, ScoreMode.Avg);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", index);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
        return esData;
    }

    @Test
    public void searchPTByORActivityAnswers4() throws Exception {
        orActivityAndAnotherAnswers("participants_structured.cmi.angio", "ANGIOABOUTYOU", "COUNTRY", "US", "CA");
    }

    public Map<String, Map<String, Object>> orActivityAndAnotherAnswers(String index, String activityCode, String stableId, String answer1, String answer2) throws Exception {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                activityAnswer.must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", stableId));
                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                orAnswers.should(QueryBuilders.matchQuery("activities.questionsAnswers.answer", answer1));
                orAnswers.should(QueryBuilders.matchQuery("activities.questionsAnswers.answer", answer2));
                activityAnswer.must(orAnswers);
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities.questionsAnswers", activityAnswer, ScoreMode.Avg);

                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                queryBuilder.must(QueryBuilders.matchQuery("activities.activityCode", activityCode).operator(Operator.AND));
                queryBuilder.must(queryActivityAnswer);
                NestedQueryBuilder query = QueryBuilders.nestedQuery("activities", queryBuilder, ScoreMode.Avg);

                BoolQueryBuilder activityAnswer2 = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("activities.completedAt");
                activityAnswer2.must(existsQuery);
                activityAnswer2.must(QueryBuilders.matchQuery("activities.activityCode", "ANGIORELEASE"));
                NestedQueryBuilder queryActivityAnswer2 = QueryBuilders.nestedQuery("activities", activityAnswer2, ScoreMode.Avg);

                BoolQueryBuilder activityAnswer3 = new BoolQueryBuilder();
                activityAnswer3.must(queryActivityAnswer2);
                activityAnswer3.must(query);

                searchSourceBuilder.query(activityAnswer3);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", index);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
        return esData;
    }

    @Test
    public void searchPTByProfileFieldNotEmpty() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-brain");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder query = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("profile.lastName");
                query.must(existsQuery);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-brain");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    @Ignore
    public void searchPTByProfileFieldEmpty() throws Exception { //TODO - not working yet
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-brain");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder query = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("profile.lastName");
                query.mustNot(existsQuery);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-brain");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByNotEmptyActivityField() throws Exception {
        notEmptyActivity("participants_structured.cmi.cmi-brain", "SURGICAL_PROCEDURES", "POSTCONSENT");
    }

    @Test
    public void searchPTByCompositeNotEmpty() throws Exception {
        notEmptyActivity("participants_structured.cmi.angio", "BIRTH_YEAR", "ANGIOABOUTYOU");
    }

    @Test
    public void testSearchParticipantById() {
        String pIdToFilter = "WUKIOQNKXJZGCAXCSYGB";
        String fetchedPid = "";
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            Optional<ElasticSearchParticipantDto> esObject =
                    ElasticSearchUtil.fetchESDataByParticipantId("participants_structured.rgp.rgp", pIdToFilter, client);
            fetchedPid = esObject.orElse(new ElasticSearchParticipantDto.Builder().build())
                    .getProfile()
                    .map(ESProfile::getLegacyAltPid)
                    .orElse("");
        } catch (IOException e) {
            Assert.fail();
            e.printStackTrace();
        }
        Assert.assertEquals(pIdToFilter, fetchedPid);
    }

    @Test
    public void testSearchParticipantByAltpid() {
        String altpid = "c4aa8c50248beb9970ac94fc913ca7bbaa625726318b5705d7e42c9d9cede4b4";
        String fetchedPid = "";
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            ElasticSearchParticipantDto esObject =
                    ElasticSearchUtil.fetchESDataByAltpid("participants_structured.atcp.atcp", altpid, client);
            fetchedPid = esObject.getProfile()
                    .map(ESProfile::getLegacyAltPid)
                    .orElse("");
        } catch (IOException e) {
            Assert.fail();
            e.printStackTrace();
        }
        Assert.assertEquals(altpid, fetchedPid);
    }

    public void notEmptyActivity(String index, String stableId, String activityCode) throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("activities.questionsAnswers.answer");
                ExistsQueryBuilder existsQuery2 = new ExistsQueryBuilder("activities.questionsAnswers.dateFields");


                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                orAnswers.should(existsQuery);
                orAnswers.should(existsQuery2);
                activityAnswer.must(orAnswers);
                activityAnswer.must(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", stableId));
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities.questionsAnswers", activityAnswer, ScoreMode.Avg);

                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                queryBuilder.must(QueryBuilders.matchQuery("activities.activityCode", activityCode).operator(Operator.AND));
                queryBuilder.must(queryActivityAnswer);
                NestedQueryBuilder query = QueryBuilders.nestedQuery("activities", queryBuilder, ScoreMode.Avg);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", index);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchNotEmptyCompletedAt() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-brain");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {

                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("activities.completedAt");
                activityAnswer.must(existsQuery);
                activityAnswer.must(QueryBuilders.matchQuery("activities.activityCode", "POSTCONSENT"));
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities", activityAnswer, ScoreMode.Avg);

                searchSourceBuilder.query(queryActivityAnswer);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-brain");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByEmptyField() throws Exception { //TODO - not working yet
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-brain");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder("activities.questionsAnswers.answer");
                activityAnswer.mustNot(existsQuery);
                activityAnswer.mustNot(QueryBuilders.matchQuery("activities.questionsAnswers.stableId", "SURGICAL_PROCEDURES"));
                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery("activities.questionsAnswers", activityAnswer, ScoreMode.Avg);

                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                queryBuilder.must(QueryBuilders.matchQuery("activities.activityCode", "POSTCONSENT").operator(Operator.AND));
                queryBuilder.must(queryActivityAnswer);
                NestedQueryBuilder query = QueryBuilders.nestedQuery("activities", queryBuilder, ScoreMode.Avg);

                searchSourceBuilder.query(query);
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-brain");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByTimestamp() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            String dateUserEntered = "2020-01-28";

            final long start = SystemUtil.getLongFromDateString(dateUserEntered);
            //set endDate to midnight of that date
            String endDate = dateUserEntered + " 23:59:59";
            final long end = SystemUtil.getLongFromDetailDateString(endDate);
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.rangeQuery("profile.createdAt").gte(start).lte(end));
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-osteo");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByDateString() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            String date1 = "2002-01-28";
            String date2 = "2002-01-29";
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.rangeQuery("dsm.dateOfBirth").gte(date1).lte(date2));
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-osteo");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTAgeUp6Month() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

                Calendar calendar = Calendar.getInstance();
                long now = calendar.getTimeInMillis();
                calendar.add(Calendar.MONTH, 6);
                long month6 = calendar.getTimeInMillis();
                sourceBuilder.query(QueryBuilders.rangeQuery("dsm.").gte(now).lte(month6));

                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", "participants_structured.cmi.cmi-osteo");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByProfileData() throws Exception {
        searchProfileValue("participants_structured.cmi.cmi-mpc", "profile.hruid", "PBQAFP");
    }

    @Test
    public void searchPTByGUID() throws Exception {
        searchProfileValue("participants_structured.cmi.cmi-mbc", "profile.guid", "N02WKXSXD1M0YFWYYR2U");
    }

    @Test
    public void searchPTByID() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            GetRequest getRequest = new GetRequest()
                    .index("participants_structured.atcp.atcp")
                    .type("_doc")
                    .id("5db65f9f43f38f2ae0ec3efb1d3325b1356e0a6ffa4b7ef71938f73930269811");
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
            Map<String, Object> map = getResponse.getSourceAsMap();
            Assert.assertNotEquals(0, map.size());
        }
    }

    public void searchProfileValue(String index, String field, String value) throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            searchSourceBuilder.query(QueryBuilders.matchQuery(field, value));
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm", index);
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    public void searchPTByLegacy() throws Exception {
        searchProfileValue("participants_structured.atcp.atcp", "profile.legacyAltPid", "5db65f9f43f38f2ae0ec3efb1d3325b1356e0a6ffa4b7ef71938f73930269811");
    }

    @Test
    public void createTestParticipantsInES() throws Exception {
        boolean addToDSMDB = true;

        String index = "participants_structured.testboston.testboston";
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            //getting a participant ES doc
//            GetRequest getRequest = new GetRequest(index, "_doc", "EG5AIEQZOJGX2HYDTQZZ");
//            GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
//            Assert.assertNotNull(response);
//4000
            for (int i = 789; i < 900; i++) {
                String guid = "TEST00000000000" + StringUtils.leftPad(String.valueOf(i), 5, "0");
                String hruid = "P" + StringUtils.leftPad(String.valueOf(i), 5, "0");
                //changing values to be able to create new participant
//                Map<String, Object> source = response.getSource();
//                Assert.assertNotNull(source);
//                Object profile = source.get("profile");
//                Assert.assertNotNull(profile);
//                ((Map<String, Object>) profile).put("hruid", hruid);
//                ((Map<String, Object>) profile).put("firstName", "Unit " + i);
//                ((Map<String, Object>) profile).put("lastName", "Test " + i);
//                ((Map<String, Object>) profile).put("guid", guid);
//                Object medicalProviders = source.get("medicalProviders");
//                List<Map<String, Object>> medicalProvidersList = ((List<Map<String, Object>>) medicalProviders);
//                int counter = 0;
//                for (Map<String, Object> medicalProviderMap : medicalProvidersList) {
//                    medicalProviderMap.put("guid", "MP0" + counter + hruid);
//
//                    //add participant and institution into DSM DB
//                    if (addToDSMDB) { //only use if you want your dsm db to have the participants as well
//                        TestHelper.addTestParticipant("Angio", guid, hruid, "MP0" + counter + hruid, "20191022", true);
//                    }
//                    counter++;
//                }
//                Assert.assertNotNull(medicalProviders);

                if (addToDSMDB) {
                    int kitCount = ThreadLocalRandom.current().nextInt(1, 6 + 1);
                    long ordered = 1607644866323L;
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(ordered);
                    for (int kits = 0; kits < kitCount; kits++) {
                        String suffix = hruid + "_" + kits;
                        if (kits != 0) {
                            calendar.add(Calendar.MONTH, 1);
                            ordered = calendar.getTimeInMillis();
                        }
                        DBTestUtil.insertLatestKitRequest(DBTestUtil.SQL_INSERT_KIT_REQUEST, cfg.getString("portal.insertKit"),
                                suffix, 6, "6", guid, ordered);
                        DBTestUtil.insertLatestKitRequest(DBTestUtil.SQL_INSERT_KIT_REQUEST, cfg.getString("portal.insertKit"),
                                suffix+"_1", 7, "6", guid, ordered);

                        int status = ThreadLocalRandom.current().nextInt(1, 4 + 1);
                        switch (status) {
                            case 1:
//                                shipped to PT
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix, "I In Transit", "M Shipment Ready for UPS","20210331 140351","20210331 140351");
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix+"_1", "FAKE_DSM_LABEL_UID" + suffix+"_1", "I In Transit", "M Shipment Ready for UPS","20210331 140351","20210331 140351");
                                break;
                            case 2:
//                                received @ PT
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix, "D Delivered", "M Shipment Ready for UPS","20210331 140351","20210331 140351");
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix+"_1", "FAKE_DSM_LABEL_UID" + suffix+"_1", "D Delivered", "M Shipment Ready for UPS","20210331 140351","20210331 140351");
                                break;
                            case 3:
//                                shipped to GP
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix, "FAKE_DSM_LABEL_UID" + suffix, "D Delivered", "I In Transit","20210331 140351","20210331 140351");
                                DBTestUtil.setKitToStatus("FAKE_SPK_UUID" + suffix+"_1", "FAKE_DSM_LABEL_UID" + suffix+"_1", "D Delivered", "I In Transit","20210331 140351","20210331 140351");
                                break;
                            case 4:
//                                returned @ GP
                                DBTestUtil.setKitToReceived("FAKE_SPK_UUID" + suffix,"FAKE_DSM_LABEL_UID" + suffix+"_1","20210331 140351","20210331 140351");
                                DBTestUtil.setKitToReceived("FAKE_SPK_UUID" + suffix+"_1","FAKE_DSM_LABEL_UID" + suffix+"_1","20210331 140351","20210331 140351");
                                break;
                        }
                    }
                }

//                //adding new participant into ES
//                IndexRequest indexRequest = new IndexRequest(index, "_doc", guid).source(source);
//                UpdateRequest updateRequest = new UpdateRequest(index, "_doc", guid).doc(source).upsert(indexRequest);
//                client.update(updateRequest, RequestOptions.DEFAULT);
//
//                //getting a participant ES doc
//                GetRequest getRequestAfter = new GetRequest(index, "_doc", guid);
//                GetResponse responseAfter = client.get(getRequestAfter, RequestOptions.DEFAULT);
//                Assert.assertNotNull(responseAfter);
//
//                //changing values to be able to create new participant
//                Map<String, Object> sourceAfter = responseAfter.getSource();
//                Assert.assertNotNull(sourceAfter);
                logger.info("added participant #" + i);
            }
        }
    }

    private Map<String, Object> getObjectByID(String index, String ddpParticipantId, String object) throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            String[] includes = new String[] {object};
            String[] excludes = Strings.EMPTY_ARRAY;
            FetchSourceContext fetchSourceContext = new FetchSourceContext(true, includes, excludes);
            GetRequest getRequest = new GetRequest()
                    .index(index)
                    .type("_doc")
                    .id(ddpParticipantId)
                    .fetchSourceContext(fetchSourceContext);
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);
            return getResponse.getSourceAsMap();
        }
    }

    @Test
    public void testRemoveWorkflowIfNoDataOrWrongSubject() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_URL),
                TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_USERNAME), TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_PASSWORD))) {
            String ddpParticipantId = "TZYO5WQ7N58HX4WSJJG0";
            String collaboratorParticipantId = "RGP_2046_3";
            DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                    false, null, false, null, "participants_structured.rgp.rgp", null, null);
            Map<String, Object> workflowsBefore = ElasticSearchUtil.getObjectsMap(client, ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");
            ElasticSearchUtil.removeWorkflowIfNoDataOrWrongSubject(client, ddpParticipantId, ddpInstance, collaboratorParticipantId);
            Map<String, Object> workflowsAfter = ElasticSearchUtil.getObjectsMap(client, ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");
            Map<String, Object> workflows = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");
            Assert.assertTrue(workflows != null && !workflows.isEmpty());
            List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) workflows.get("workflows");
            Assert.assertTrue(workflowListES != null && !workflowListES.isEmpty());
            for (Map<String, Object> workflowES : workflowListES) {
                Map<String, String> data = (Map<String, String>) workflowES.get("data");
                Assert.assertTrue(data != null);
                String subjectId = data.get("subjectId");
                Assert.assertTrue(!collaboratorParticipantId.equalsIgnoreCase(subjectId));
            }

            ElasticSearchUtil.updateRequest(client, ddpParticipantId, ddpInstance.getParticipantIndexES(), workflowsBefore);
        }
    }

    @Test
    public void updateWorkflowValues() throws Exception {
        String ddpParticipantId = "XLDUNC3BHGWGWERHW783";
        String workflow = "ALIVE_DECEASED";
        String status = "TEST";
        DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                false, null, false, null, "participants_structured.atcp.atcp", null, null);

        Map<String, Object> workflowsBefore = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");

        ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstance(ddpInstance, ddpParticipantId,
                workflow, status), false);
        Map<String, Object> workflows = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");

        if (workflows != null && !workflows.isEmpty()) {
            List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) workflows.get("workflows");
            if (workflowListES != null && !workflowListES.isEmpty()) {
                for (Map<String, Object> workflowES : workflowListES) {
                    if (workflow.equals(workflowES.get("workflow"))) {
                        Assert.assertTrue(status.equals(workflowES.get("status")));
                    }
                }
            }
        }

        String newStatus = "DECEASED";
        ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstance(ddpInstance, ddpParticipantId,
                workflow, newStatus), false);
        Map<String, Object> updatedWorkflows = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");

        if (updatedWorkflows != null && !updatedWorkflows.isEmpty()) {
            List<Map<String, Object>> updatedWorkflowsListES = (List<Map<String, Object>>) updatedWorkflows.get("workflows");
            if (updatedWorkflowsListES != null && !updatedWorkflowsListES.isEmpty()) {
                for (Map<String, Object> workflowES : updatedWorkflowsListES) {
                    if (workflow.equals(workflowES.get("workflow"))) {
                        Assert.assertTrue(newStatus.equals(workflowES.get("status")));
                    }
                }
            }
        }
        Assert.assertEquals(workflows.size(), updatedWorkflows.size());

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstance.getParticipantIndexES(), workflowsBefore);
    }

    @Test
    public void updateWorkflowValuesWithStudySpecificData() throws Exception {
        String ddpParticipantId = "XLDUNC3BHGWGWERHW781";
        String workflow = "ALIVE_DECEASED";
        String status = "ALIVE";
        String subjectId = "testId";
        String firstname = "testfirstname";
        String lastname = "testlastname";
        DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                false, null, false, null, "participants_structured.rgp.rgp", null, null);
        Map<String, Object> workflowsBefore = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");

        ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstanceWithStudySpecificData(ddpInstance, ddpParticipantId,
                workflow, status, new WorkflowForES.StudySpecificData(subjectId, firstname, lastname)), false);

        testWorkflowWithStudySpecificData(ddpParticipantId, workflow, status, subjectId, firstname, lastname, ddpInstance);

        ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstanceWithStudySpecificData(ddpInstance, ddpParticipantId,
                workflow, status, new WorkflowForES.StudySpecificData(subjectId, firstname, lastname)), true);

        testWorkflowWithStudySpecificData(ddpParticipantId, workflow, status, subjectId, firstname, lastname, ddpInstance);

        String newSubjectId = "testId3";

        ElasticSearchUtil.writeWorkflow(WorkflowForES.createInstanceWithStudySpecificData(ddpInstance, ddpParticipantId,
                workflow, status, new WorkflowForES.StudySpecificData(newSubjectId, firstname, lastname)), false);

        testWorkflowWithStudySpecificData(ddpParticipantId, workflow, status, subjectId, firstname, lastname, ddpInstance);

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstance.getParticipantIndexES(), workflowsBefore);

    }

    public void testWorkflowWithStudySpecificData(String ddpParticipantId, String workflow, String status, String subjectId, String firstname, String lastname, DDPInstance ddpInstance) throws Exception {
        Map<String, Object> workflows = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "workflows");
        Assert.assertTrue(workflows != null && !workflows.isEmpty());
        List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) workflows.get("workflows");
        Assert.assertTrue(workflowListES != null && !workflowListES.isEmpty());
        boolean dataFound = false;
        for (Map<String, Object> workflowES : workflowListES) {
            Map<String, String> data = (Map<String, String>) workflowES.get("data");
            if (data == null) {
                continue;
            } else {
                dataFound = true;
            }
            if (workflow.equals(workflowES.get("workflow")) && subjectId.equals(data.get("subjectId"))) {
                Assert.assertEquals(status, workflowES.get("status"));
                Assert.assertEquals(firstname, data.get("firstname"));
                Assert.assertEquals(lastname, data.get("lastname"));
            }
        }
        Assert.assertTrue(dataFound);
    }

    @Test
    public void updateDSMObjects() throws Exception {
        Integer id = 5729;
        String ddpParticipantId = "XLDUNC3BHGWGWERHW781";
        String objectType = ESObjectConstants.MEDICAL_RECORDS;
        String idName = ESObjectConstants.MEDICAL_RECORDS_ID;
        DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                false, null, false, null, "participants_structured.rgp.rgp", null, null);
        String familyId = "1234";

        Map<String, Object> objectsMapESBefore = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "dsm");

        Map<String, Object> nameValues = new HashMap<>();
        nameValues.put("name", "testName");
        nameValues.put("type", "testType");
        nameValues.put("requested", "2020-02-29");
        nameValues.put("received", "2020-02-29");


        ElasticSearchUtil.writeDsmRecord(ddpInstance, id, ddpParticipantId, objectType, idName, nameValues);
        ElasticSearchUtil.writeDsmRecord(ddpInstance, null, ddpParticipantId, ESObjectConstants.FAMILY_ID, familyId, null);

        Map<String, Object> objectsMapESAfter = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "dsm");

        if (objectsMapESAfter != null && !objectsMapESAfter.isEmpty()) {
            Object dsmObject = objectsMapESAfter.get("dsm");
            Map<String, Object> dsmMap = new ObjectMapper().convertValue(dsmObject, Map.class);
            List<Map<String, Object>> objectList = (List<Map<String, Object>>) dsmMap.get(objectType);
            if (objectList != null && !objectList.isEmpty()) {
                for (Map<String, Object> object : objectList) {
                    if (id.equals(object.get(idName))) {
                        Assert.assertEquals(5729, object.get(idName));
                        Assert.assertEquals("testName", object.get("name"));
                        Assert.assertEquals("testType", object.get("type"));
                        Assert.assertEquals("2020-02-29", object.get("requested"));
                        Assert.assertEquals("2020-02-29", object.get("received"));
                    }
                }
            }
            String familyIdFromES = dsmMap.get(ESObjectConstants.FAMILY_ID).toString();
            Assert.assertEquals(familyId, familyIdFromES);
        }

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstance.getParticipantIndexES(), objectsMapESBefore);
    }

    @Test
    public void updateDSMObjectsTissue() throws Exception {
        Integer id = 5730;
        String ddpParticipantId = "XLDUNC3BHGWGWERHW781";
        String objectType = ESObjectConstants.TISSUE_RECORDS;
        String idName = ESObjectConstants.TISSUE_RECORDS_ID;
        DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                false, null, false, null, "participants_structured.rgp.rgp", null, null);

        Map<String, Object> objectsMapESBefore = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "dsm");

        Map<String, Object> nameValues = new HashMap<>();
        nameValues.put("typePx", "testType");
        nameValues.put("locationPx", "testLocation");
        nameValues.put("datePx", "2020-02-29");
        nameValues.put("histology", "testType");
        nameValues.put("accessionNumber", "423423233232");
        nameValues.put("requested", "2020-02-29");
        nameValues.put("received", "2020-02-29");
        nameValues.put("sent", "2020-02-29");

        ElasticSearchUtil.writeDsmRecord(ddpInstance, id, ddpParticipantId, objectType, idName, nameValues);

        Map<String, Object> objectsMapESAfter = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, "dsm");

        if (objectsMapESAfter != null && !objectsMapESAfter.isEmpty()) {
            Object dsmObject = objectsMapESAfter.get("dsm");
            Map<String, Object> dsmMap = new ObjectMapper().convertValue(dsmObject, Map.class);
            List<Map<String, Object>> objectList = (List<Map<String, Object>>) dsmMap.get(objectType);
            if (objectList != null && !objectList.isEmpty()) {
                for (Map<String, Object> object : objectList) {
                    if (id.equals(object.get(idName))) {
                        Assert.assertEquals(5730, object.get(idName));
                        Assert.assertEquals("testType", object.get("typePx"));
                        Assert.assertEquals("testLocation", object.get("locationPx"));
                        Assert.assertEquals("2020-02-29", object.get("datePx"));
                        Assert.assertEquals("testType", object.get("histology"));
                        Assert.assertEquals("423423233232", object.get("accessionNumber"));
                        Assert.assertEquals("2020-02-29", object.get("requested"));
                        Assert.assertEquals("2020-02-29", object.get("received"));
                        Assert.assertEquals("2020-02-29", object.get("sent"));
                    }
                }
            }
        }

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstance.getParticipantIndexES(), objectsMapESBefore);
    }

    @Test
    public void updateSamples() throws Exception {
        String id = "5729";
        String ddpParticipantId = "XLDUNC3BHGWGWERHW781";
        String objectType = ESObjectConstants.SAMPLES;
        String idName = ESObjectConstants.KIT_REQUEST_ID;
        DDPInstance ddpInstance = new DDPInstance(null,null, null, null, false, 0, 0,
                false, null, false, null, "participants_structured.rgp.rgp", null, null);

        Map<String, Object> objectsMapESBefore = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, objectType);

        Map<String, Object> nameValues = new HashMap<>();
        nameValues.put("kitType", "testType");
        nameValues.put("kitLabel", "testLabel");
        nameValues.put("bspCollaboratorSampleId", "testCollaboratorSampleId");
        nameValues.put("bspCollaboratorParticipantId", "testCollaboratorParticipantId");
        nameValues.put("trackingOut", "testtrackingOut");
        nameValues.put("trackingIn", "testtrackingIn");
        nameValues.put("carrier", "testCarrier");
        nameValues.put("sent", "2020-02-29");
        nameValues.put("delivered", "2020-02-29");
        nameValues.put("received", "2020-02-29");

        ElasticSearchUtil.writeSample(ddpInstance, id, ddpParticipantId, objectType, idName, nameValues);

        Map<String, Object> objectsMapESAfter = ElasticSearchUtil.getObjectsMap(ddpInstance.getParticipantIndexES(), ddpParticipantId, objectType);

        if (objectsMapESAfter != null && !objectsMapESAfter.isEmpty()) {
            List<Map<String, Object>> objectList = (List<Map<String, Object>>) objectsMapESAfter.get(objectType);
            if (objectList != null && !objectList.isEmpty()) {
                for (Map<String, Object> object : objectList) {
                    if (id.equals(object.get(idName))) {
                        Assert.assertEquals("testType", object.get("kitType"));
                        Assert.assertEquals("testLabel", object.get("kitLabel"));
                        Assert.assertEquals("testCollaboratorSampleId", object.get("bspCollaboratorSampleId"));
                        Assert.assertEquals("testCollaboratorParticipantId", object.get("bspCollaboratorParticipantId"));
                        Assert.assertEquals("testtrackingOut", object.get("trackingOut"));
                        Assert.assertEquals("testtrackingIn", object.get("trackingIn"));
                        Assert.assertEquals("testCarrier", object.get("carrier"));
                        Assert.assertEquals("2020-02-29", object.get("sent"));
                        Assert.assertEquals("2020-02-29", object.get("delivered"));
                        Assert.assertEquals("2020-02-29", object.get("received"));
                    }
                }
            }
        }

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstance.getParticipantIndexES(), objectsMapESBefore);
    }

    private static void updateES(String index, String ddpParticipantId, Map<String, Object> jsonMap) throws Exception{
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            UpdateRequest updateRequest = new UpdateRequest()
                    .index(index)
                    .type("_doc")
                    .id(ddpParticipantId)
                    .doc(jsonMap)
                    .docAsUpsert(true);

            UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
        }
    }

    @Test
    public void deletePT() throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {

            DeleteRequest deleteRequest = new DeleteRequest()
                    .index("participants_structured.atcp.atcp")
                    .type("_doc")
                    .id("5db65f9f43f38f2ae0ec3efb1d3325b1356e0a6ffa4b7ef71938f73930269811");
            DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
        }
    }
}
