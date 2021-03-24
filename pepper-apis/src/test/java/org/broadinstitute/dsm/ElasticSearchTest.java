package org.broadinstitute.dsm;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
            SearchRequest searchRequest = new SearchRequest("participants_structured.cmi.cmi-osteo");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
            SearchRequest searchRequest = new SearchRequest("activity_definition.atcp.atcp");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
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
    public void mbcLegacyPTGUID() throws Exception {
        searchProfileValue("participants_structured.cmi.cmi-osteo", "profile.guid", "ZSNS8E4U838JPW7NU93Y");
    }

    @Test
    public void mbcLegacyPTAltPID() throws Exception {
        searchProfileValue("participants_structured.cmi.cmi-mbc", "profile.legacyAltPid", "8195-A16");
    }

    public void searchProfileValue(String index, String field, String value) throws Exception {
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            int scrollSize = 1000;
            Map<String, Map<String, Object>> esData = new HashMap<>();
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            SearchResponse response = null;
            int i = 0;
            while (response == null || response.getHits().getHits().length != 0) {
                searchSourceBuilder.query(QueryBuilders.matchQuery(field, value)); //works!

                searchSourceBuilder.size(scrollSize);
                searchSourceBuilder.from(i * scrollSize);
                searchRequest.source(searchSourceBuilder);

                response = client.search(searchRequest, RequestOptions.DEFAULT);
                ElasticSearchUtil.addingParticipantStructuredHits(response, esData, "realm");
                i++;
            }
            Assert.assertNotEquals(0, esData.size());
        }
    }

    @Test
    @Ignore
    public void createTestParticipantsInES() throws Exception {
        boolean addToDSMDB = false;

        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"))) {
            //getting a participant ES doc
            GetRequest getRequest = new GetRequest("participants_structured.cmi.angio", "_doc", "98JBYLZI33O0IFUMH9CS");
            GetResponse response = client.get(getRequest, RequestOptions.DEFAULT);
            Assert.assertNotNull(response);

            for (int i = 0; i < 100; i++) {
                String guid = "TEST000000000000000" + i;
                String hruid = "PT000" + i;
                //changing values to be able to create new participant
                Map<String, Object> source = response.getSource();
                Assert.assertNotNull(source);
                Object profile = source.get("profile");
                Assert.assertNotNull(profile);
                ((Map<String, Object>) profile).put("hruid", hruid);
                ((Map<String, Object>) profile).put("firstName", "Unit " + i);
                ((Map<String, Object>) profile).put("lastName", "Test " + i);
                ((Map<String, Object>) profile).put("guid", guid);
                Object medicalProviders = source.get("medicalProviders");
                List<Map<String, Object>> medicalProvidersList = ((List<Map<String, Object>>) medicalProviders);
                int counter = 0;
                for (Map<String, Object> medicalProviderMap : medicalProvidersList) {
                    medicalProviderMap.put("guid", "MP0" + counter + hruid);

                    //add participant and institution into DSM DB
                    if (addToDSMDB) { //only use if you want your dsm db to have the participants as well
                        TestHelper.addTestParticipant("Angio", guid, hruid, "MP0" + counter + hruid, "20191022", true);
                    }
                    counter++;
                }
                Assert.assertNotNull(medicalProviders);

                //adding new participant into ES
                IndexRequest indexRequest = new IndexRequest("participants_structured.cmi.angio", "_doc", guid).source(source);
                UpdateRequest updateRequest = new UpdateRequest("participants_structured.cmi.angio", "_doc", guid).doc(source).upsert(indexRequest);
                client.update(updateRequest, RequestOptions.DEFAULT);

                //getting a participant ES doc
                GetRequest getRequestAfter = new GetRequest("participants_structured.cmi.angio", "_doc", guid);
                GetResponse responseAfter = client.get(getRequestAfter, RequestOptions.DEFAULT);
                Assert.assertNotNull(responseAfter);

                //changing values to be able to create new participant
                Map<String, Object> sourceAfter = responseAfter.getSource();
                Assert.assertNotNull(sourceAfter);
                logger.info("added participant #" + i);
            }
        }
    }
}
