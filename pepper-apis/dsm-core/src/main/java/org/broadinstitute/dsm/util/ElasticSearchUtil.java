package org.broadinstitute.dsm.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.MedicalInfo;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.export.WorkflowForES;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.model.elastic.ESAddress;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearch;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.gbf.Address;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;

public class ElasticSearchUtil {

    private static final Logger logger = LoggerFactory.getLogger(DDPRequestUtil.class);

    public static final String ACTIVITIES = "activities";
    public static final String QUESTIONS_ANSWER = "questionsAnswers";
    private static final String ACTIVITIES_QUESTIONS_ANSWER = "activities.questionsAnswers";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_ANSWER = "activities.questionsAnswers.answer";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_GROUPED_OPTIONS = "activities.questionsAnswers.groupedOptions";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_NESTED_OPTIONS = "activities.questionsAnswers.nestedOptions";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_DATE_FIELDS = "activities.questionsAnswers.dateFields";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_DATE = "activities.questionsAnswers.date";
    private static final String ACTIVITIES_QUESTIONS_ANSWER_STABLE_ID = "activities.questionsAnswers.stableId";
    public static final String PROFILE = "profile";
    public static final String DATA = "data";
    public static final String PROXIES = "proxies";
    public static final String DSM = "dsm";
    public static final String ACTIVITY_CODE = "activityCode";
    public static final String ACTIVITY_VERSION = "activityVersion";
    public static final String ADDRESS = "address";
    public static final String INVITATIONS = "invitations";
    public static final String PDFS = "pdfs";
    public static final String GUID = "guid";
    public static final String LEGACY_ALT_PID = "legacyAltPid";
    public static final String BY_GUID = " AND profile.guid = ";
    public static final String BY_PROFILE_GUID = "profile.guid = ";
    public static final String EMPTY = "empty";
    public static final String BY_HRUID = " AND profile.hruid = ";
    public static final String BY_GUIDS = " OR profile.guid = ";
    public static final String BY_LEGACY_ALTPID = " AND profile.legacyAltPid = ";
    public static final String BY_PROFILE_LEGACY_ALTPID = "profile.legacyAltPid = ";
    public static final String AND = " AND (";
    public static final String ES = "ES";
    public static final String CLOSING_PARENTHESIS = ")";
    public static final String ESCAPE_CHARACTER_DOT_SEPARATOR = "\\.";
    public static final String BY_LEGACY_ALTPIDS = " OR profile.legacyAltPid = ";
    public static final String BY_LEGACY_SHORTID = " AND profile.legacyShortId = ";
    public static final String END_OF_DAY = " 23:59:59";
    public static final String CREATED_AT = "createdAt";
    public static final String COMPLETED_AT = "completedAt";
    public static final String LAST_UPDATED = "lastUpdatedAt";
    public static final String STATUS = "status";
    public static final String PROFILE_CREATED_AT = "profile." + CREATED_AT;
    public static final String PROFILE_GUID = "profile.guid";
    public static final String PROFILE_LEGACYALTPID = "profile.legacyAltPid";
    public static final String WORKFLOWS = "workflows";
    public static final String EMAIL_FIELD = "email";
    public static final String PARTICIPANTS_STRUCTURED_ANY = "participants_structured.*";
    public static final String TYPE = "type";
    public static final String TEXT = "text";
    public static final String KEYWORD = ".keyword";
    public static final String PROPERTIES = "properties";
    public static final byte OUTER_FIELD_INDEX = 0;
    public static final byte INNER_FIELD_INDEX = 1;


    // These clients are expensive. They internally have thread pools and other resources. Let's
    // create one instance and reuse it as much as possible. Client is thread-safe per the docs.
    private static RestHighLevelClient client;
    private static Map<String, MappingMetaData> fieldMappings;

    static {
        initClient();
        fetchFieldMappings();
    }

    public static synchronized void initClient() {
        if (client == null) {
            try {
                client = getClientForElasticsearchCloud(
                        TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_URL),
                        TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_USERNAME),
                        TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_PASSWORD));
            } catch (MalformedURLException e) {
                throw new RuntimeException("Error while initializing ES client", e);
            }
        }
    }

    private static synchronized void fetchFieldMappings() {
        GetMappingsRequest request = new GetMappingsRequest();
        request.indices(PARTICIPANTS_STRUCTURED_ANY);
        try {
            logger.info("Getting ES data field mapping");
            fieldMappings = getClientInstance().indices()
                    .getMapping(request, RequestOptions.DEFAULT)
                    .mappings();
        } catch (IOException e) {
            throw new RuntimeException("Error while fetching field mappings from ES", e);
        }
    }

    public static RestHighLevelClient getClientInstance() {
        // This should have been initialized once at the start, so we're not locking to avoid concurrency overhead.
        return client;
    }

    public static RestHighLevelClient getClientForElasticsearchCloud(@NonNull String baseUrl,
                                                                     @NonNull String userName,
                                                                     @NonNull String password) throws MalformedURLException {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        URL url = new URL(baseUrl);
        String proxy = TransactionWrapper.hasConfigPath(ApplicationConfigConstants.ES_PROXY)
                ? TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.ES_PROXY) : null;
        return getClientForElasticsearchCloud(baseUrl, userName, password, proxy);
    }

    public static RestHighLevelClient getClientForElasticsearchCloudCF(@NonNull String baseUrl,
                                                                       @NonNull String userName,
                                                                       @NonNull String password,
                                                                       String proxy) throws MalformedURLException {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        URL url = new URL(baseUrl);
        return getClientForElasticsearchCloud(baseUrl, userName, password, proxy);
    }

    public static RestHighLevelClient getClientForElasticsearchCloud(@NonNull String baseUrl,
                                                                     @NonNull String userName,
                                                                     @NonNull String password,
                                                                     String proxy) throws MalformedURLException {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        URL url = new URL(baseUrl);
        URL proxyUrl = (proxy != null && !proxy.isBlank()) ? new URL(proxy) : null;
        if (proxyUrl != null) {
            logger.info("Using Elasticsearch client proxy: {}", proxyUrl);
        }

        RestClientBuilder builder = RestClient.builder(new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    if (proxyUrl != null) {
                        httpClientBuilder.setProxy(new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol()));
                        httpClientBuilder.setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE);
                    }
                    return httpClientBuilder;
                })
                .setMaxRetryTimeoutMillis(100000);

        return new RestHighLevelClient(builder);
    }

    public static Map<String, Map<String, Object>> getSingleParticipantFromES(@NonNull String realm,
                                                                              @NonNull String index,
                                                                              RestHighLevelClient client,
                                                                              String participantHruid) {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        if (StringUtils.isNotBlank(index)) {
            logger.info("Collecting ES data");
            try {
                int scrollSize = 1000;
                SearchRequest searchRequest = new SearchRequest(index);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                SearchResponse response = null;
                int i = 0;
                searchSourceBuilder.query(QueryBuilders.matchQuery("profile.hruid", participantHruid)).sort(PROFILE_CREATED_AT, SortOrder.ASC);
                while (response == null || response.getHits().getHits().length != 0) {
                    searchSourceBuilder.size(scrollSize);
                    searchSourceBuilder.from(i * scrollSize);
                    searchRequest.source(searchSourceBuilder);

                    response = client.search(searchRequest, RequestOptions.DEFAULT);
                    addingParticipantStructuredHits(response, esData, realm, index);
                    i++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Couldn't get participants from ES for instance " + realm, e);
            }
            logger.info("Got " + esData.size() + " participants from ES for instance " + realm);
        }
        return esData;
    }


    public static Map<String, Map<String, Object>> getDDPParticipantsFromES(@NonNull String realm,
                                                                            @NonNull String index,
                                                                            RestHighLevelClient client) {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        if (StringUtils.isNotBlank(index)) {
            logger.info("Collecting ES data");
            try {
                int scrollSize = 1000;
                SearchRequest searchRequest = new SearchRequest(index);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                SearchResponse response = null;
                int i = 0;
                searchSourceBuilder.query(QueryBuilders.matchAllQuery()).sort(PROFILE_CREATED_AT, SortOrder.ASC);
                while (response == null || response.getHits().getHits().length != 0) {
                    searchSourceBuilder.size(scrollSize);
                    searchSourceBuilder.from(i * scrollSize);
                    searchRequest.source(searchSourceBuilder);

                    response = client.search(searchRequest, RequestOptions.DEFAULT);
                    addingParticipantStructuredHits(response, esData, realm, index);
                    i++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Couldn't get participants from ES for instance " + realm, e);
            }
            logger.info("Got " + esData.size() + " participants from ES for instance " + realm);
        }
        return esData;
    }

    public static Optional<ElasticSearchParticipantDto> getParticipantESDataByParticipantId(@NonNull String index, @NonNull String participantId) {
        Optional<ElasticSearchParticipantDto> elasticSearch = Optional.empty();
        logger.info("Getting ES data for participant: " + participantId);
        try {
            elasticSearch = fetchESDataByParticipantId(index, participantId, client);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get ES for participant: " + participantId + " from " + index, e);
        }
        logger.info("Got ES data for participant: " + participantId + " from " + index);
        return elasticSearch;
    }

    public static ElasticSearchParticipantDto getParticipantESDataByAltpid(@NonNull String index, @NonNull String altpid) {
        ElasticSearchParticipantDto elasticSearch = new ElasticSearchParticipantDto.Builder().build();
        logger.info("Getting ES data for participant: " + altpid);
        try {
            elasticSearch = fetchESDataByAltpid(index, altpid, client);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get ES for participant: " + altpid + " from " + index, e);
        }
        logger.info("Got ES data for participant: " + altpid + " from " + index);
        return elasticSearch;
    }

    public static Optional<ElasticSearchParticipantDto> fetchESDataByParticipantId(String index, String participantId, RestHighLevelClient client) throws IOException {
        String matchQueryName = ParticipantUtil.isGuid(participantId) ? "profile.guid" : "profile.legacyAltPid";
        return Optional.of(getElasticSearchForGivenMatch(index, participantId, client, matchQueryName));
    }

    public static ElasticSearchParticipantDto fetchESDataByAltpid(String index, String altpid, RestHighLevelClient client) throws IOException {
        String matchQueryName = "profile.legacyAltPid";
        return getElasticSearchForGivenMatch(index, altpid, client, matchQueryName);
    }

    public static ElasticSearchParticipantDto getElasticSearchForGivenMatch(String index, String id, RestHighLevelClient client, String matchQueryName) throws IOException {
        SearchRequest searchRequest = new SearchRequest(index);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchResponse response = null;
        searchSourceBuilder.query(QueryBuilders.matchQuery(matchQueryName, id)).sort(PROFILE_CREATED_AT, SortOrder.ASC);
        searchSourceBuilder.size(1);
        searchSourceBuilder.from(0);
        searchRequest.source(searchSourceBuilder);

        response = client.search(searchRequest, RequestOptions.DEFAULT);
        response.getHits();
        ElasticSearch elasticSearch = new ElasticSearch();
        return elasticSearch.parseSourceMap(response.getHits().getTotalHits() > 0 ? response.getHits().getAt(0).getSourceAsMap() : null).get();
    }

    public static Map<String, Map<String, Object>> getDDPParticipantsFromES(@NonNull String realm, @NonNull String index) {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        if (StringUtils.isNotBlank(index)) {
            logger.info("Collecting ES data from index: " + index);
            try {
                esData = getDDPParticipantsFromES(realm, index, client);
            } catch (Exception e) {
                logger.error("Couldn't get participants from ES for instance " + realm, e);
            }
            logger.info("Finished collecting ES data");
        }
        return esData;
    }

    public static Map<String, Map<String, Object>> getFilteredDDPParticipantsFromES(@NonNull DDPInstance instance, @NonNull String filter) {
        String index = instance.getParticipantIndexES();
        if (StringUtils.isNotBlank(index)) {
            Map<String, Map<String, Object>> esData = new HashMap<>();
            logger.info("Collecting ES data");
            try {
                int scrollSize = 1000;
                SearchRequest searchRequest = new SearchRequest(index);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                SearchResponse response = null;
                int i = 0;
                AbstractQueryBuilder query = createESQuery(filter);
                if (query == null) {
                    throw new RuntimeException("Couldn't create query from filter " + filter);
                }
                searchSourceBuilder.query(query).sort(PROFILE_CREATED_AT, SortOrder.ASC);
                while (response == null || response.getHits().getHits().length != 0) {
                    searchSourceBuilder.size(scrollSize);
                    searchSourceBuilder.from(i * scrollSize);
                    searchRequest.source(searchSourceBuilder);

                    response = client.search(searchRequest, RequestOptions.DEFAULT);
                    addingParticipantStructuredHits(response, esData, instance.getName(), index);
                    i++;
                }
            } catch (Exception e) {
                logger.error("Couldn't get participants from ES for instance " + instance.getName(), e);
            }
            logger.info("Got " + esData.size() + " participants from ES for instance " + instance.getName());
            return esData;
        }
        return null;
    }

    public static Map<String, Address> getParticipantAddresses(RestHighLevelClient client, String indexName, Set<String> participantGuids) {
        Gson gson = new Gson();
        Map<String, Address> addressByParticipant = new HashMap<>();
        int scrollSize = 100;
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchResponse response = null;
        int hitNumber = 0;
        int pageNumber = 0;
        long totalHits = 0;


        BoolQueryBuilder qb = QueryBuilders.boolQuery();
        qb.must(QueryBuilders.termsQuery("profile.guid", participantGuids));

        searchSourceBuilder.fetchSource(new String[]{PROFILE, ADDRESS}, null);
        searchSourceBuilder.query(qb).sort(PROFILE_CREATED_AT, SortOrder.ASC).docValueField(ADDRESS).docValueField(PROFILE);
        while (pageNumber == 0 || hitNumber < totalHits) {
            searchSourceBuilder.size(scrollSize);
            searchSourceBuilder.from(pageNumber * scrollSize);
            searchRequest.source(searchSourceBuilder);

            try {
                response = client.search(searchRequest, RequestOptions.DEFAULT);
                totalHits = response.getHits().getTotalHits();
                pageNumber++;
            } catch (IOException e) {
                throw new RuntimeException("Could not query elastic index " + indexName + " for " + participantGuids.size() + " participants", e);
            }
            for (SearchHit hit : response.getHits()) {
                Map<String, Object> participantRecord = hit.getSourceAsMap();
                JsonObject participantJson = new JsonParser().parse(new Gson().toJson(participantRecord)).getAsJsonObject();
                if (participantJson.has(ADDRESS) && participantJson.has(PROFILE)) {
                    ESAddress address = gson.fromJson(participantJson.get(ADDRESS), ESAddress.class);
                    ESProfile profile = gson.fromJson(participantJson.get(PROFILE), ESProfile.class);
                    Address gbfAddress = new Address(address.getRecipient(), address.getStreet1(), address.getStreet1(),
                            address.getCity(), address.getState(), address.getZip(), address.getCountry(), address.getPhone());
                    addressByParticipant.put(profile.getGuid(), gbfAddress);
                }
                hitNumber++;
            }

        }
        return addressByParticipant;
    }

    public static void removeWorkflowIfNoDataOrWrongSubject(String ddpParticipantId, DDPInstance ddpInstance, String collaboratorParticipantId) {
        removeWorkflowIfNoDataOrWrongSubject(client, ddpParticipantId, ddpInstance, collaboratorParticipantId);
    }

    public static void removeWorkflowIfNoDataOrWrongSubject(RestHighLevelClient client, String ddpParticipantId, DDPInstance ddpInstance, String collaboratorParticipantId) {
        String index = ddpInstance.getParticipantIndexES();
        try {
            Map<String, Object> workflowMapES = getObjectsMap(client, index, ddpParticipantId, ESObjectConstants.WORKFLOWS);
            if (workflowMapES == null || workflowMapES.isEmpty()) {
                return;
            }
            List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) workflowMapES.get(ESObjectConstants.WORKFLOWS);
            boolean removed = workflowListES.removeIf(workflow -> !workflow.containsKey(ESObjectConstants.DATA) ||
                    (((Map) workflow.get(ESObjectConstants.DATA)).get(ESObjectConstants.SUBJECT_ID) != null
                            && (collaboratorParticipantId != null && collaboratorParticipantId.equals(((Map) workflow.get(ESObjectConstants.DATA)).get(ESObjectConstants.SUBJECT_ID)))));
            if (!removed) {
                return;
            }
            if (client != null) {
                updateRequest(client, ddpParticipantId, index, workflowMapES);
            } else {
                updateRequest(ddpParticipantId, index, workflowMapES);
            }
        } catch (Exception e) {
            logger.error("Couldn't remove workflows for participant " + ddpParticipantId + " to ES index " + ddpInstance.getParticipantIndexES() + " for instance " + ddpInstance.getName(), e);
        }
    }

    public static void writeWorkflow(@NonNull WorkflowForES workflowForES, boolean clearBeforeUpdate) {
        writeWorkflow(client, workflowForES, clearBeforeUpdate);
    }

    public static void writeWorkflow(RestHighLevelClient client, @NonNull WorkflowForES workflowForES, boolean clearBeforeUpdate) {
        String ddpParticipantId = workflowForES.getDdpParticipantId();
        DDPInstance instance = workflowForES.getInstance();
        String index = instance.getParticipantIndexES();
        if (StringUtils.isBlank(index)) {
            return;
        }
        try {

            String participantId = ParticipantUtil.isGuid(ddpParticipantId) ? ddpParticipantId : getParticipantESDataByAltpid(client, index, ddpParticipantId)
                    .getProfile()
                    .map(ESProfile::getGuid)
                    .orElse(ddpParticipantId);

            Map<String, Object> workflowMapES = getObjectsMap(client, index, participantId, ESObjectConstants.WORKFLOWS);
            String workflow = workflowForES.getWorkflow();
            String status = workflowForES.getStatus();
            if (workflowMapES != null && !workflowMapES.isEmpty() && !clearBeforeUpdate) {
                List<Map<String, Object>> workflowListES = (List<Map<String, Object>>) workflowMapES.get(ESObjectConstants.WORKFLOWS);
                if (workflowListES != null && !workflowListES.isEmpty()) {
                    if (workflowForES.getStudySpecificData() != null) {
                        updateWorkflowStudySpecific(workflow, status, workflowListES, workflowForES.getStudySpecificData());
                    } else {
                        updateWorkflow(workflow, status, workflowListES);
                    }
                } else {
                    workflowMapES = addWorkflows(workflow, status, workflowForES.getStudySpecificData());
                }
            } else {
                workflowMapES = addWorkflows(workflow, status, workflowForES.getStudySpecificData());
            }

            if (client != null) {
                updateRequest(client, ddpParticipantId, index, workflowMapES);
            } else {
                updateRequest(ddpParticipantId, index, workflowMapES);
            }
        } catch (Exception e) {
            logger.error("Couldn't write workflow information for participant " + ddpParticipantId + " to ES index " + instance.getParticipantIndexES() + " for instance " + instance.getName(), e);
        }
    }

    public static Map<String, Object> addWorkflows(String workflow, String status, WorkflowForES.StudySpecificData studySpecificData) {
        Map<String, Object> workflowMapES;
        Map<String, Object> newWorkflowMap = new HashMap<>(Map.of(
                ESObjectConstants.WORKFLOW, workflow,
                STATUS, status,
                ESObjectConstants.DATE, SystemUtil.getISO8601DateString()
        ));
        if (studySpecificData != null) {
            newWorkflowMap.put(ESObjectConstants.DATA, new ObjectMapper().convertValue(studySpecificData, Map.class));
        }
        List<Map<String, Object>> workflowList = new ArrayList<>();
        workflowList.add(newWorkflowMap);
        workflowMapES = new HashMap<>();
        workflowMapES.put(ESObjectConstants.WORKFLOWS, workflowList);
        return workflowMapES;
    }

    public static boolean updateWorkflowStudySpecific(String workflow, String status, List<Map<String, Object>> workflowListES,
                                                      WorkflowForES.StudySpecificData studySpecificData) {
        boolean updated = false;
        for (Map<String, Object> workflowES : workflowListES) {
            Map<String, String> data = (Map<String, String>) workflowES.get("data");
            String existingSubjectId = null;
            if (data != null) {
                existingSubjectId = data.get(ESObjectConstants.SUBJECT_ID);
                if (workflow.equals(workflowES.get(ESObjectConstants.WORKFLOW)) && existingSubjectId != null
                        && studySpecificData.getSubjectId().equals(existingSubjectId)) {
                    //update value in existing workflow
                    updated = updateWorkflowFieldsStudySpecific(status, studySpecificData, workflowES);
                    break;
                }
            } else {
                if (workflow.equals(workflowES.get(ESObjectConstants.WORKFLOW))) {
                    updated = updateWorkflowFieldsStudySpecific(status, studySpecificData, workflowES);
                    break;
                }
            }
        }
        if (!updated) {
            //add workflow
            workflowListES.add(new HashMap<>(Map.of(
                    ESObjectConstants.WORKFLOW, workflow,
                    STATUS, status,
                    ESObjectConstants.DATE, SystemUtil.getISO8601DateString(),
                    ESObjectConstants.DATA, new ObjectMapper().convertValue(studySpecificData, Map.class)
            )));
        }
        return updated;
    }

    public static boolean updateWorkflowFieldsStudySpecific(String status, WorkflowForES.StudySpecificData studySpecificData, Map<String, Object> workflowES) {
        workflowES.put(STATUS, status);
        workflowES.put(ESObjectConstants.DATE, SystemUtil.getISO8601DateString());
        workflowES.put(ESObjectConstants.DATA, new ObjectMapper().convertValue(studySpecificData, Map.class));
        return true;
    }

    public static boolean updateWorkflow(String workflow, String status, List<Map<String, Object>> workflowListES) {
        boolean updated = false;
        for (Map<String, Object> workflowES : workflowListES) {
            if (workflow.equals(workflowES.get(ESObjectConstants.WORKFLOW))) {
                //update value in existing workflow
                workflowES.put(STATUS, status);
                workflowES.put(ESObjectConstants.DATE, SystemUtil.getISO8601DateString());
                updated = true;
                break;
            }
        }
        if (!updated) {
            //add workflow
            workflowListES.add(new HashMap<>(Map.of(
                    ESObjectConstants.WORKFLOW, workflow,
                    STATUS, status,
                    ESObjectConstants.DATE, SystemUtil.getISO8601DateString()
            )));
        }
        return updated;
    }

    public static void writeDsmRecord(@NonNull DDPInstance instance,
                                      Integer id,
                                      @NonNull String ddpParticipantId,
                                      @NonNull String objectType,
                                      @NonNull String idName,
                                      Map<String, Object> nameValues) {
        writeDsmRecord(client, instance, id, ddpParticipantId, objectType, idName, nameValues);
    }

    public static void writeDsmRecord(RestHighLevelClient client, @NonNull DDPInstance instance,
                                      Integer id,
                                      @NonNull String ddpParticipantId,
                                      @NonNull String objectType,
                                      @NonNull String idName,
                                      Map<String, Object> nameValues) {
        String index = instance.getParticipantIndexES();
        try {
            if (StringUtils.isNotBlank(index)) {
                Map<String, Object> objectsMapES = getObjectsMap(client, index, ddpParticipantId, ESObjectConstants.DSM);
                if (ESObjectConstants.FAMILY_ID.equals(objectType)) {
                    if (objectsMapES != null && !objectsMapES.isEmpty()) {
                        Map<String, Object> esDsmObjectMap = (Map<String, Object>) objectsMapES.get(ESObjectConstants.DSM);
                        esDsmObjectMap.put(objectType, idName);
                    } else {
                        Map<String, Object> mapForDSM = new HashMap<>();
                        mapForDSM.put(objectType, idName);
                        objectsMapES = new HashMap<>();
                        objectsMapES.put(ESObjectConstants.DSM, mapForDSM);
                    }
                } else if (objectsMapES != null && !objectsMapES.isEmpty()) {
                    Object dsmObject = objectsMapES.get(ESObjectConstants.DSM);
                    Map<String, Object> dsmMap = new ObjectMapper().convertValue(dsmObject, Map.class);
                    updateOrCreateMap(id, objectType, nameValues, idName, dsmMap);
                } else {
                    List<Map<String, Object>> objectList = new ArrayList<>();
                    createAndAddNewObjectMap(id, objectList, idName, nameValues);
                    Map<String, Object> mapForDSM = new HashMap<>();
                    objectsMapES = new HashMap<>();
                    mapForDSM.put(objectType, objectList);
                    objectsMapES.put(ESObjectConstants.DSM, mapForDSM);
                }

                if (client != null) {
                    updateRequest(client, ddpParticipantId, index, objectsMapES);
                } else {
                    updateRequest(ddpParticipantId, index, objectsMapES);
                }
                logger.info("Updated " + objectType + " information for participant " + ddpParticipantId + " in ES for instance " + instance.getName());
            }
        } catch (Exception e) {
            logger.error("Couldn't write " + objectType + " information for participant " + ddpParticipantId + " to ES index " + instance.getParticipantIndexES() + " for instance " + instance.getName(), e);
        }
    }

    public static void writeSample(@NonNull DDPInstance instance,
                                   @NonNull String id,
                                   @NonNull String ddpParticipantId,
                                   @NonNull String objectType,
                                   String idName, Map<String, Object> nameValues) {
        writeSample(client, instance, id, ddpParticipantId, objectType, idName, nameValues);
    }

    public static void writeSample(RestHighLevelClient client, @NonNull DDPInstance instance,
                                   @NonNull String id,
                                   @NonNull String ddpParticipantId,
                                   @NonNull String objectType,
                                   String idName, Map<String, Object> nameValues) {
        String index = instance.getParticipantIndexES();
        try {
            if (StringUtils.isNotBlank(index)) {
                Map<String, Object> objectsMapES = getObjectsMap(client, index, ddpParticipantId, objectType);
                if (objectsMapES != null && !objectsMapES.isEmpty()) {
                    updateOrCreateMap(id, objectType, nameValues, idName, objectsMapES);
                } else {
                    List<Map<String, Object>> objectList = new ArrayList<>();
                    createAndAddNewObjectMap(id, objectList, idName, nameValues);
                    objectsMapES = new HashMap<>();
                    objectsMapES.put(objectType, objectList);
                }

                updateRequest(client, ddpParticipantId, index, objectsMapES);
                logger.info("Updated " + objectType + " information for participant " + ddpParticipantId + " in ES for instance " + instance.getName());
            }
        } catch (Exception e) {
            logger.error("Couldn't write " + objectType + " information for participant " + ddpParticipantId + " to ES index " + instance.getParticipantIndexES() + " for instance " + instance.getName(), e);
        }
    }

    public static void updateOrCreateMap(@NonNull Object id, @NonNull String objectType, @NonNull Map<String, Object> nameValues, @NonNull String idName, Map<String, Object> objectsMapES) {
        List<Map<String, Object>> objectList = (List<Map<String, Object>>) objectsMapES.get(objectType);
        if (objectList != null) {
            boolean updated = false;
            for (Map<String, Object> object : objectList) {
                if (id.toString().equals(object.get(idName).toString())) {
                    for (Map.Entry<String, Object> entry : nameValues.entrySet()) {
                        if (!entry.getKey().equals(idName) && !entry.getKey().equals(ESObjectConstants.DDP_PARTICIPANT_ID)) {
                            object.put(entry.getKey(), entry.getValue());
                        }
                    }
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                createAndAddNewObjectMap(id, objectList, idName, nameValues);
            }
        } else {
            objectList = new ArrayList<>();
            createAndAddNewObjectMap(id, objectList, idName, nameValues);
            objectsMapES.put(objectType, objectList);
        }
    }

    public static void updateRequest(@NonNull String ddpParticipantId, String index, Map<String, Object> objectsMapES) throws IOException {
        updateRequest(ddpParticipantId, index, objectsMapES, client);
    }

    private static void updateRequest(@NonNull String ddpParticipantId, String index, Map<String, Object> objectsMapES,
                                      RestHighLevelClient client) throws IOException {
        String participantId = ParticipantUtil.isGuid(ddpParticipantId) ? ddpParticipantId : getParticipantESDataByAltpid(client, index, ddpParticipantId)
                .getProfile()
                .map(ESProfile::getGuid)
                .orElse(ddpParticipantId);
        UpdateRequest updateRequest = new UpdateRequest()
                .index(index)
                .type("_doc")
                .id(participantId)
                .doc(objectsMapES)
                .docAsUpsert(true)
                .retryOnConflict(5);

        UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
        logger.info("Update workflow information for participant " + ddpParticipantId + " to ES index " + index);
    }

    private static ElasticSearchParticipantDto getParticipantESDataByAltpid(RestHighLevelClient client, String index, String altpid) {
        ElasticSearchParticipantDto elasticSearch = new ElasticSearchParticipantDto.Builder().build();

        logger.info("Getting ES data for participant: " + altpid);
        try {
            elasticSearch = fetchESDataByAltpid(index, altpid, client);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get ES for participant: " + altpid + " from " + index, e);
        }
        logger.info("Got ES data for participant: " + altpid + " from " + index);

        return elasticSearch;
    }

    public static void updateRequest(RestHighLevelClient client, @NonNull String ddpParticipantId, String index, Map<String, Object> objectsMapES) throws IOException {
        if (client != null) {
            updateRequest(ddpParticipantId, index, objectsMapES, client);
        } else {
            logger.error("RestHighLevelClient was null");
        }
    }

    public static void createAndAddNewObjectMap(@NonNull Object id, List<Map<String, Object>> objectList, String idName, @NonNull Map<String, Object> nameValues) {
        Map<String, Object> newObjectMap = new HashMap<>();
        newObjectMap.put(idName, id);
        for (Map.Entry<String, Object> entry : nameValues.entrySet()) {
            if (!entry.getKey().equals(idName) && !entry.getKey().equals(ESObjectConstants.DDP_PARTICIPANT_ID)) {
                newObjectMap.put(entry.getKey(), entry.getValue());
            }
        }
        objectList.add(newObjectMap);
    }

    public static Optional<ESProfile> getParticipantProfileByGuidOrAltPid(String index, String guidOrAltPid) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.boolQuery()
                    .should(QueryBuilders.termQuery(PROFILE_GUID, guidOrAltPid))
                    .should(QueryBuilders.termQuery(PROFILE_LEGACYALTPID, guidOrAltPid)));
            searchSourceBuilder.size(1);
            searchSourceBuilder.from(0);
            searchSourceBuilder.fetchSource(new String[]{PROFILE}, null);

            SearchRequest searchRequest = new SearchRequest(index);
            searchRequest.source(searchSourceBuilder);

            logger.info("Getting ES profile for participant with guid/altpid: {}", guidOrAltPid);
            SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

            ESProfile profile = null;
            if (response.getHits().getTotalHits() > 0) {
                Map<String, Object> source = response.getHits().getAt(0).getSourceAsMap();
                profile = new ElasticSearch().parseSourceMap(source).flatMap(ElasticSearchParticipantDto::getProfile).orElse(null);
                if (profile != null) {
                    logger.info("Found ES profile for participant, guid: {} altpid: {}", profile.getGuid(), profile.getLegacyAltPid());
                }
            }
            return Optional.ofNullable(profile);
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching ES profile for participant guid/altpid: " + guidOrAltPid + " from " + index, e);
        }
    }

    public static Map<String, Object> getObjectsMap(RestHighLevelClient client, String index, String id, String object) throws Exception {
        String[] includes = new String[]{object};
        String[] excludes = Strings.EMPTY_ARRAY;
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, includes, excludes);
        GetRequest getRequest = new GetRequest()
                .index(index)
                .type("_doc")
                .id(id)
                .fetchSourceContext(fetchSourceContext);

        GetResponse getResponse = null;
        if (client.exists(getRequest, RequestOptions.DEFAULT)) {
            getResponse = client.get(getRequest, RequestOptions.DEFAULT);
        }
        return getResponse != null ? getResponse.getSourceAsMap() : Collections.emptyMap();
    }

    public static Map<String, Object> getObjectsMap(String index, String id, String object) throws Exception {
        return getObjectsMap(client, index, id, object);
    }

    public static DDPParticipant getParticipantAsDDPParticipant(@NonNull Map<String, Map<String, Object>> participantsESData, @NonNull String ddpParticipantId) {
        if (participantsESData != null && !participantsESData.isEmpty()) {
            Map<String, Object> participantESData = participantsESData.get(ddpParticipantId);
            if (participantESData != null && !participantESData.isEmpty()) {
                Map<String, Object> address = (Map<String, Object>) participantESData.get(ADDRESS);
                Map<String, Object> profile = (Map<String, Object>) participantESData.get(PROFILE);
                if (address != null && !address.isEmpty() && profile != null && !profile.isEmpty()) {
                    String firstName = "";
                    String lastName = "";
                    if (StringUtils.isNotBlank((String) profile.get("firstName")) && StringUtils.isNotBlank((String) profile.get("lastName"))) {
                        firstName = (String) profile.get("firstName");
                        lastName = (String) profile.get("lastName");
                    } else {
                        lastName = (String) address.get("mailToName");
                    }
                    return new DDPParticipant(ddpParticipantId, firstName, lastName,
                            (String) address.get("country"), (String) address.get("city"), (String) address.get("zip"),
                            (String) address.get("street1"), (String) address.get("street2"), (String) address.get("state"),
                            (String) profile.get(ESObjectConstants.HRUID), null);
                } else if (profile != null && !profile.isEmpty()) {
                    return new DDPParticipant((String) profile.get(ESObjectConstants.HRUID), "", (String) profile.get("firstName"), (String) profile.get("lastName"));
                }
            }
        }
        return null;
    }

    public static MedicalInfo getParticipantAsMedicalInfo(@NonNull Map<String, Map<String, Object>> participantsESData, @NonNull String ddpParticipantId) {
        if (participantsESData != null && !participantsESData.isEmpty()) {
            Map<String, Object> participantESData = participantsESData.get(ddpParticipantId);
            if (participantESData != null && !participantESData.isEmpty()) {
                Map<String, Object> dsm = (Map<String, Object>) participantESData.get(DSM);
                if (dsm != null && !dsm.isEmpty()) {
                    boolean hasConsentedToBloodDraw = (boolean) dsm.get("hasConsentedToBloodDraw");
                    boolean hasConsentedToTissueSample = (boolean) dsm.get("hasConsentedToTissueSample");
                    MedicalInfo medicalInfo = new MedicalInfo(ddpParticipantId);
                    medicalInfo.setDateOfDiagnosis(dsm.get("diagnosisMonth") + "/" + dsm.get("diagnosisYear"));
                    medicalInfo.setDob((String) dsm.get("dateOfBirth"));
                    medicalInfo.setDrawBloodConsent(hasConsentedToBloodDraw ? 1 : 0);
                    medicalInfo.setTissueSampleConsent(hasConsentedToTissueSample ? 1 : 0);
                    return medicalInfo;
                }
            }
        }
        return null;
    }

    public static String getPreferredLanguage(@NonNull Map<String, Map<String, Object>> participantsESData, @NonNull String ddpParticipantId) {
        if (participantsESData != null && !participantsESData.isEmpty()) {
            Map<String, Object> participantESData = participantsESData.get(ddpParticipantId);
            if (participantESData != null && !participantESData.isEmpty()) {
                Map<String, Object> profile = (Map<String, Object>) participantESData.get(PROFILE);
                if (profile != null && !profile.isEmpty()) {
                    return (String) profile.get("preferredLanguage");
                }
            }
        }
        return null;
    }

    //simple is better than complex, KISS(Keep It Simple Stupid)
    public static AbstractQueryBuilder<? extends AbstractQueryBuilder<?>> createESQuery(@NonNull String filter) {
        String[] filters = filter.split(Filter.AND);
        BoolQueryBuilder finalQuery = new BoolQueryBuilder();

        // This map used to store query path associated with a field name (to be compared, for example: `SELF_CURRENT_AGE`)
        // this data is used in method findQueryBuilderForFieldName() in order to properly find a range of numbers which
        // is created out of such filter like f.ex: `PREQUAL.SELF_CURRENT_AGE >= 20 AND PREQUAL.SELF_CURRENT_AGE <= 46`
        Map<String, String> queryPartsMap = new HashMap<>();

        // store NestedQueryBuilder which is a parent of a Range of numbers
        NestedQueryBuilder parentNestedOfRangeBuilderOfNumbers = null;

        for (String f : filters) {
            if (StringUtils.isNotBlank(f) && f.contains(DBConstants.ALIAS_DELIMITER)) {
                if (f.contains(Filter.EQUALS) || f.contains(Filter.LIKE)) {
                    BoolQueryBuilder innerQuery = new BoolQueryBuilder();
                    f = f.replace("(", "").replace(")", "").trim();
                    if (f.contains(Filter.OR)) {
                        String[] orValues = f.split(Filter.OR);
                        for (String or : orValues) {
                            createQuery(innerQuery, or, false);
                        }
                        finalQuery.must(innerQuery);
                    } else {
                        createQuery(finalQuery, f, true);
                    }
                } else if (f.contains(Filter.LARGER_EQUALS)) {
                    String[] nameValue = f.split(Filter.LARGER_EQUALS);
                    String userEntered = nameValue[1].replaceAll("'", "").trim();

                    if (StringUtils.isNotBlank(nameValue[0])) {
                        if (nameValue[0].startsWith(PROFILE) || nameValue[0].startsWith(ADDRESS)) {
                            try {
                                long date = SystemUtil.getLongFromString(userEntered);
                                QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, nameValue[0].trim());
                                if (tmpBuilder != null) {
                                    ((RangeQueryBuilder) tmpBuilder).gte(date);
                                } else {
                                    finalQuery.must(QueryBuilders.rangeQuery(nameValue[0].trim()).gte(date));
                                }
                            } catch (ParseException e) {
                                finalQuery.must(QueryBuilders.matchQuery(nameValue[0].trim(), userEntered));
                            }
                        } else if (nameValue[0].startsWith(DSM)) {
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, nameValue[0].trim());
                            if (tmpBuilder != null) {
                                ((RangeQueryBuilder) tmpBuilder).gte(userEntered);
                            } else {
                                finalQuery.must(QueryBuilders.rangeQuery(nameValue[0].trim()).gte(userEntered));
                            }
                        } else if (nameValue[0].startsWith(DATA)) {
                            String[] dataParam = nameValue[0].split("\\.");
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, dataParam[1].trim());
                            try {
                                long date = SystemUtil.getLongFromString(userEntered);
                                if (tmpBuilder != null) {
                                    ((RangeQueryBuilder) tmpBuilder).gte(date);
                                } else {
                                    finalQuery.must(QueryBuilders.rangeQuery(dataParam[1].trim()).gte(date));
                                }
                            } catch (ParseException e) {
                                logger.error("range was not date. user entered: " + userEntered);
                            }
                        } else if (nameValue[0].startsWith(INVITATIONS)) {
                            String[] invitationParam = nameValue[0].split("\\.");
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, invitationParam[1].trim());
                            try {
                                long date = SystemUtil.getLongFromString(userEntered);
                                if (tmpBuilder != null) {
                                    ((RangeQueryBuilder) tmpBuilder).gte(date);
                                } else {
                                    finalQuery.must(QueryBuilders.rangeQuery(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim()).gte(date));
                                }
                            } catch (ParseException e) {
                                logger.error("range was not date. user entered: " + userEntered);
                            }
                        } else {
                            String[] surveyParam = nameValue[0].split("\\.");
                            if (CREATED_AT.equals(surveyParam[1].trim()) || COMPLETED_AT.equals(surveyParam[1].trim()) || LAST_UPDATED.equals(surveyParam[1].trim())) {
                                QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, surveyParam[1].trim());
                                try {
                                    long date = SystemUtil.getLongFromString(userEntered);
                                    if (tmpBuilder != null) {
                                        ((RangeQueryBuilder) tmpBuilder).gte(date);
                                    } else {
                                        tmpBuilder = new BoolQueryBuilder();
                                        ((BoolQueryBuilder) tmpBuilder).must(QueryBuilders.rangeQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim()).gte(date));
                                        BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                                        activityAnswer.must(tmpBuilder);
                                        activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + ACTIVITY_CODE, surveyParam[0].trim()).operator(Operator.AND));
                                        NestedQueryBuilder query = QueryBuilders.nestedQuery(ACTIVITIES, activityAnswer, ScoreMode.Avg);
                                        finalQuery.must(query);
                                    }
                                } catch (ParseException e) {
                                    logger.error("range was not date. user entered: " + userEntered);
                                }
                            } else if (StringUtils.isNumeric(userEntered)) { // separately process expressions with numbers
                                NestedQueryBuilder query = addRangeLimitForNumber(
                                        Filter.LARGER_EQUALS, surveyParam[1].trim(), userEntered, finalQuery, queryPartsMap, ACTIVITIES_QUESTIONS_ANSWER_ANSWER);
                                if (query != null) {
                                    parentNestedOfRangeBuilderOfNumbers = query;
                                }
                            } else if (userEntered.trim().matches("\\d{4}-\\d{1,2}-\\d{1,2}")) { // separately process expressions with numbers
                                NestedQueryBuilder query = addRangeLimitForNumber(
                                        Filter.LARGER_EQUALS, surveyParam[1].trim(), userEntered, finalQuery, queryPartsMap, ACTIVITIES_QUESTIONS_ANSWER_DATE);
                                if (query != null) {
                                    parentNestedOfRangeBuilderOfNumbers = query;
                                }
                            }
                        }
                    } else {
                        logger.error("one of the following is null: fieldName: " + nameValue[0] + " userEntered: [hidingValueInCasePHI]");
                    }
                } else if (f.contains(Filter.SMALLER_EQUALS)) {
                    String[] nameValue = f.split(Filter.SMALLER_EQUALS);
                    String userEntered = nameValue[1].replaceAll("'", "").trim();

                    if (StringUtils.isNotBlank(nameValue[0])) {
                        if (nameValue[0].startsWith(PROFILE) || nameValue[0].startsWith(ADDRESS)) {
                            String endDate = userEntered + END_OF_DAY;
                            long date = SystemUtil.getLongFromDetailDateString(endDate);
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, nameValue[0].trim());
                            if (tmpBuilder != null) {
                                ((RangeQueryBuilder) tmpBuilder).lte(date);
                            } else {
                                finalQuery.must(QueryBuilders.rangeQuery(nameValue[0].trim()).lte(date));
                            }
                        } else if (nameValue[0].startsWith(DSM)) {
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, nameValue[0].trim());
                            if (tmpBuilder != null) {
                                ((RangeQueryBuilder) tmpBuilder).lte(userEntered);
                            } else {
                                finalQuery.must(QueryBuilders.rangeQuery(nameValue[0].trim()).lte(userEntered));
                            }
                        } else if (nameValue[0].startsWith(DATA)) {
                            String[] dataParam = nameValue[0].split("\\.");
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, dataParam[1].trim());
                            try {
                                long date = SystemUtil.getLongFromString(userEntered);
                                if (tmpBuilder != null) {
                                    ((RangeQueryBuilder) tmpBuilder).lte(date);
                                } else {
                                    finalQuery.must(QueryBuilders.rangeQuery(dataParam[1].trim()).lte(date));
                                }
                            } catch (ParseException e) {
                                logger.error("range was not date. user entered: " + userEntered);
                            }
                        } else if (nameValue[0].startsWith(INVITATIONS)) {
                            String[] invitationParam = nameValue[0].split("\\.");
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, invitationParam[1].trim());
                            try {
                                long date = SystemUtil.getLongFromString(userEntered);
                                if (tmpBuilder != null) {
                                    ((RangeQueryBuilder) tmpBuilder).lte(date);
                                } else {
                                    finalQuery.must(QueryBuilders.rangeQuery(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim()).lte(date));
                                }
                            } catch (ParseException e) {
                                logger.error("range was not date. user entered: " + userEntered);
                            }
                        } else {
                            String[] surveyParam = nameValue[0].split("\\.");
                            if (CREATED_AT.equals(surveyParam[1].trim()) || COMPLETED_AT.equals(surveyParam[1].trim()) || LAST_UPDATED.equals(surveyParam[1].trim())) {
                                QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, surveyParam[1].trim());
                                try {
                                    long date = SystemUtil.getLongFromString(userEntered);
                                    if (tmpBuilder != null) {
                                        ((RangeQueryBuilder) tmpBuilder).gte(date);
                                    } else {
                                        tmpBuilder = new BoolQueryBuilder();
                                        ((BoolQueryBuilder) tmpBuilder).must(QueryBuilders.rangeQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim()).lte(date));
                                        BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                                        activityAnswer.must(tmpBuilder);
                                        activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + ACTIVITY_CODE, surveyParam[0].trim()).operator(Operator.AND));
                                        NestedQueryBuilder query = QueryBuilders.nestedQuery(ACTIVITIES, activityAnswer, ScoreMode.Avg);
                                        finalQuery.must(query);
                                    }
                                } catch (ParseException e) {
                                    logger.error("range was not date. user entered: " + userEntered);
                                }
                            } else if (StringUtils.isNumeric(userEntered)) { // separately process expressions with numbers
                                NestedQueryBuilder query = addRangeLimitForNumber(
                                        Filter.SMALLER_EQUALS, surveyParam[1].trim(), userEntered, finalQuery, queryPartsMap, ACTIVITIES_QUESTIONS_ANSWER_ANSWER);
                                if (query != null) {
                                    parentNestedOfRangeBuilderOfNumbers = query;
                                }
                            } else if (userEntered.trim().matches("\\d{4}-\\d{1,2}-\\d{1,2}")) { // separately process expressions with numbers
                                NestedQueryBuilder query = addRangeLimitForNumber(
                                        Filter.SMALLER_EQUALS, surveyParam[1].trim(), userEntered, finalQuery, queryPartsMap, ACTIVITIES_QUESTIONS_ANSWER_DATE);
                                if (query != null) {
                                    parentNestedOfRangeBuilderOfNumbers = query;
                                }
                            }
                        }
                    } else {
                        logger.error("one of the following is null: fieldName: " + nameValue[0] + " userEntered: [hidingValueInCasePHI]");
                    }
                } else if (f.contains(Filter.IS_NOT_NULL)) {
                    String[] nameValue = f.split(Filter.IS_NOT_NULL);
                    if (StringUtils.isNotBlank(nameValue[0])) {
                        if (nameValue[0].startsWith(PROFILE) || nameValue[0].startsWith(ADDRESS) || nameValue[0].startsWith(DSM)) {
                            ExistsQueryBuilder existsQuery = new ExistsQueryBuilder(nameValue[0].trim());
                            finalQuery.must(existsQuery);
                        } else if (nameValue[0].startsWith(DATA)) {
                            String[] dataParam = nameValue[0].split("\\.");
                            ExistsQueryBuilder existsQuery = new ExistsQueryBuilder(dataParam[1].trim());
                            finalQuery.must(existsQuery);
                        } else if (nameValue[0].startsWith(INVITATIONS)) {
                            String[] invitationParam = nameValue[0].split("\\.");
                            ExistsQueryBuilder existsQuery = new ExistsQueryBuilder(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim());
                            finalQuery.must(existsQuery);
                        } else {
                            String[] surveyParam = nameValue[0].split("\\.");
                            if (CREATED_AT.equals(surveyParam[1].trim()) || COMPLETED_AT.equals(surveyParam[1].trim()) || LAST_UPDATED.equals(surveyParam[1].trim()) || STATUS.equals(surveyParam[1].trim())) {
                                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim());
                                activityAnswer.must(existsQuery);
                                activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + ACTIVITY_CODE, surveyParam[0].trim()));
                                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery(ACTIVITIES, activityAnswer, ScoreMode.Avg);
                                finalQuery.must(queryActivityAnswer);
                            } else {
                                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                                ExistsQueryBuilder existsQuery = new ExistsQueryBuilder(ACTIVITIES_QUESTIONS_ANSWER_ANSWER);
                                ExistsQueryBuilder existsQuery2 = new ExistsQueryBuilder(ACTIVITIES_QUESTIONS_ANSWER_DATE_FIELDS);
                                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                                orAnswers.should(existsQuery);
                                orAnswers.should(existsQuery2);
                                activityAnswer.must(orAnswers);
                                activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_STABLE_ID, surveyParam[1].trim()));
                                NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery(ACTIVITIES_QUESTIONS_ANSWER, activityAnswer, ScoreMode.Avg);

                                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
                                queryBuilder.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + ACTIVITY_CODE, surveyParam[0].trim()).operator(Operator.AND));
                                queryBuilder.must(queryActivityAnswer);
                                NestedQueryBuilder query = QueryBuilders.nestedQuery(ACTIVITIES, queryBuilder, ScoreMode.Avg);
                                finalQuery.must(query);
                                finalQuery = processIsNotNullForRangeOfNumbers(
                                        surveyParam[1].trim(), activityAnswer, finalQuery, queryPartsMap, parentNestedOfRangeBuilderOfNumbers);
                            }
                        }
                    } else {
                        logger.error("one of the following is null: fieldName: " + nameValue[0] + " userEntered: [hidingValueInCasePHI]");
                    }
                } else if (f.contains(Filter.IS_NULL)) {

                } else {
                    logger.error("Filter could not be parsed");
                }
            }
        }
        if (finalQuery.hasClauses()) {
            return finalQuery;
        }
        return null;
    }

    private static QueryBuilder findQueryBuilderForFieldName(BoolQueryBuilder finalQuery, String fieldName) {
        return findQueryBuilderForFieldName(finalQuery, fieldName, null);
    }

    private static QueryBuilder findQueryBuilderForFieldName(BoolQueryBuilder finalQuery, String fieldName, Map<String, String> queryPartsMap) {
        QueryBuilder tmpBuilder = findQueryBuilder(finalQuery.must(), fieldName, queryPartsMap);
        if (tmpBuilder != null) {
            return tmpBuilder;
        } else {
            return findQueryBuilder(finalQuery.should(), fieldName, queryPartsMap);
        }
    }

    /**
     * Find a query which was added to finalQuery for the same [field_name]
     *
     * @param tmpFilters    parent block must() of a final query
     * @param fieldName     name of a field which queried
     * @param queryPartsMap map with queries paths which was added for field names
     */
    private static QueryBuilder findQueryBuilder(List<QueryBuilder> tmpFilters, @NonNull String fieldName, Map<String, String> queryPartsMap) {
        QueryBuilder tmpBuilder = null;
        if (!tmpFilters.isEmpty()) {
            for (Iterator<QueryBuilder> iterator = tmpFilters.iterator(); iterator.hasNext() && tmpBuilder == null; ) {
                QueryBuilder builder = iterator.next();
                if (builder instanceof RangeQueryBuilder &&
                        (((RangeQueryBuilder) builder).fieldName().equals(fieldName) ||
                                queryPartsMap != null && fieldName.equals(queryPartsMap.get(((RangeQueryBuilder) builder).fieldName())))) {
                    tmpBuilder = builder;
                } else if (builder instanceof NestedQueryBuilder) {
                    tmpBuilder = findQueryBuilder(((BoolQueryBuilder) ((NestedQueryBuilder) builder).query()).must(), fieldName, queryPartsMap);
                } else {
                    String name = builder.getName();
                    if (fieldName.equals(name)) {
                        tmpBuilder = builder;
                    } else if (builder instanceof BoolQueryBuilder && ((BoolQueryBuilder) builder).should() != null) {
                        List<QueryBuilder> shouldQueries = ((BoolQueryBuilder) builder).should();
                        for (QueryBuilder should : shouldQueries) {
                            if (should instanceof MatchQueryBuilder) {
                                String otherName = ((MatchQueryBuilder) should).fieldName();
                                if (StringUtils.isNotBlank(otherName) && (fieldName.equals(otherName) ||
                                        queryPartsMap != null && fieldName.equals(queryPartsMap.get(otherName)))) {
                                    tmpBuilder = builder;
                                }
                            }
                        }
                    }
                }
            }
        }
        return tmpBuilder;
    }

    /**
     * Process a Range limit expression for numeric values.<br>
     * Examples of numeric range limit expressions: `PREQUAL.SELF_CURRENT_AGE >= 20`, `PREQUAL.SELF_CURRENT_AGE <= 46`.<br>
     * Range can have 1 or 2 limits.<br>
     * Examples of ranges:
     * <pre>
     * PREQUAL.SELF_CURRENT_AGE >= 20 AND PREQUAL.SELF_CURRENT_AGE <= 46 - range with 2 limits
     * PREQUAL.SELF_CURRENT_AGE >= 20  - range with 1st limit only
     * PREQUAL.SELF_CURRENT_AGE <= 46  - range with 2nd limit only
     * </pre>
     *
     * <b>Algorithm:</b>
     * <ul>
     *     <li>find in `finalQuery' already created range for the same `fieldName';</li>
     *     <li>if a range is found then add a limit into this range;</li>
     *     <li>if a range is NOT found then:
     *        1) create a Range and wrap it into Nested block;
     *        2) add Nested block to `finalQuery` (note: later it can be removed and added into a block together with `IS NOT NULL'
     *        expression (for same field), if such expression exists);
     *        3) Store in `queryPartsMap` a pair `activities.questionsAnswers.answer`-> 'fieldName`
     *        in order to be possible to find Range limits (for same `fieldName`) and find `IS NOT NULL` for same `fieldName`.
     *     </li>
     * </ul>
     *
     * @param operatorType     operator type (Filter.LARGER_EQUALS or Filter.SMALLER_EQUALS)
     * @param fieldName        name of a field (for example 'SELF_CURRENT_AGE')
     * @param userEnteredValue range value (for example '20')
     * @param finalQuery       query where to add a created Range (wrapped into Nested block)
     * @param queryPartsMap    map where to save query name and fieldName - in order to find all queries related to
     *                         a same fieldName (for example related tp `SELF_CURRENT_AGE`)
     * @return NestedQueryBuilder - built query containing the numeric Range
     */
    private static NestedQueryBuilder addRangeLimitForNumber (
            String operatorType,
            String fieldName,
            String userEnteredValue,
            BoolQueryBuilder finalQuery,
            Map<String, String> queryPartsMap,
            String questionAnswerType) {

        QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, fieldName, queryPartsMap);
        if (tmpBuilder != null) {
            if (operatorType.equals(Filter.LARGER_EQUALS)) {
                ((RangeQueryBuilder) tmpBuilder).gte(userEnteredValue);
            } else {
                ((RangeQueryBuilder) tmpBuilder).lte(userEnteredValue);
            }
        } else {
            tmpBuilder = new BoolQueryBuilder();
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(questionAnswerType);
            if (operatorType.equals(Filter.LARGER_EQUALS)) {
                ((BoolQueryBuilder) tmpBuilder).must(rangeQueryBuilder.gte(userEnteredValue));
            } else {
                ((BoolQueryBuilder) tmpBuilder).must(rangeQueryBuilder.lte(userEnteredValue));
            }
            NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery(ACTIVITIES, tmpBuilder, ScoreMode.Avg);
            finalQuery.must(nestedQueryBuilder);
            queryPartsMap.put(questionAnswerType, fieldName);
            return nestedQueryBuilder;
        }
        return null;
    }

    /**
     * This method called when processed an expression `IS NOT NULL` for the case of Range of numbers.
     * If Range of numbers already exists and added
     * search for queries for the same fieldName:
     * if found and it is Range and it was inserted into Nested block then remove
     * this Range from former place and add it into must() block together with this
     * `activities.questionsAnswers.stableId`==[field_name] query.
     *
     * @param fieldName        name of a field (for example 'SELF_CURRENT_AGE')
     * @param activityAnswer   sub-query (inside `finalQuery`) where to add a Range of numbers
     * @param finalQuery       a resulting ElasticSearch query
     * @param queryPartsMap    map where to save query name and fieldName - in order to find all queries related to
     *                         a same fieldName (for example related tp `SELF_CURRENT_AGE`)
     * @param parentNestedOfRangeBuilderOfNumbers  reference to NestedQueryBuilder containing a Range of numbers
     * @return BoolQueryBuilder  finalQuery: it can be the same finalQuery or it can be reorganized finalQuery
     *    where RangeQueryBuilder removed from the initial place inside finalQuery and added into a must()-block
     *    together with `IS NOT NULL` query (for a field `fieldName`)
     */
    private static BoolQueryBuilder processIsNotNullForRangeOfNumbers(
            String fieldName,
            BoolQueryBuilder activityAnswer,
            BoolQueryBuilder finalQuery,
            Map<String, String> queryPartsMap,
            NestedQueryBuilder parentNestedOfRangeBuilderOfNumbers) {

        QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, fieldName, queryPartsMap);
        if (tmpBuilder instanceof RangeQueryBuilder && parentNestedOfRangeBuilderOfNumbers != null) {
            activityAnswer.must(tmpBuilder);
            BoolQueryBuilder finalQueryReorganized = new BoolQueryBuilder();
            finalQuery.must().forEach( builder -> {
                if (!builder.equals(parentNestedOfRangeBuilderOfNumbers)) {
                    finalQueryReorganized.must(builder);
                }
            });
            return finalQueryReorganized;
        }
        return finalQuery;
    }

    public static void addingParticipantStructuredHits(@NonNull SearchResponse response, Map<String, Map<String, Object>> esData,
                                                       String ddp, String index) {
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            sourceMap.put("ddp", ddp);
            if (sourceMap.containsKey(PROFILE)) {
                if (ElasticSearchUtil.isESUsersIndex(index)) {
                    esData.put(hit.getId(), sourceMap);
                    continue;
                }
                String legacyId = (String) ((Map<String, Object>) sourceMap.get(PROFILE)).get(LEGACY_ALT_PID);
                if (StringUtils.isNotBlank(legacyId)) {
                    esData.put(legacyId, sourceMap);
                } else {
                    esData.put(hit.getId(), sourceMap);
                }
            } else {
                logger.warn("Participant {} doesn't have profile information", hit.getId());
            }
        }
    }

    public static Map<String, Map<String, Object>> getActivityDefinitions(@NonNull DDPInstance instance) {
        Map<String, Map<String, Object>> esData = new HashMap<>();
        String index = instance.getActivityDefinitionIndexES();
        if (StringUtils.isNotBlank(index)) {
            logger.info("Collecting activity definitions from ES");
            try {
                int scrollSize = 1000;

                SearchRequest searchRequest = new SearchRequest(index);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                SearchResponse response = null;
                int i = 0;
                while (response == null || response.getHits().getHits().length != 0) {
                    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
                    searchSourceBuilder.size(scrollSize);
                    searchSourceBuilder.from(i * scrollSize);
                    searchRequest.source(searchSourceBuilder);

                    response = client.search(searchRequest, RequestOptions.DEFAULT);
                    addingActivityDefinitionHits(response, esData);
                    i++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Couldn't get activity definition from ES for instance " + instance.getName(), e);
            }
            logger.info("Got " + esData.size() + " activity definitions from ES for instance " + instance.getName());
        }
        return esData;
    }

    public static void addingActivityDefinitionHits(@NonNull SearchResponse response, Map<String, Map<String, Object>> esData) {
        for (SearchHit hit : response.getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            String activityCode = (String) sourceMap.get(ACTIVITY_CODE);
            String activityVersion = (String) sourceMap.get(ACTIVITY_VERSION);
            if (StringUtils.isNotBlank(activityCode)) {
                esData.put(activityCode + "_" + activityVersion, sourceMap);
            } else {
                esData.put(hit.getId(), sourceMap);
            }
        }
    }

    private static void createQuery(@NonNull BoolQueryBuilder finalQuery, @NonNull String filterPart, boolean must) {
        boolean wildCard = false;
        String[] nameValue = filterPart.split(Filter.EQUALS);
        if (nameValue.length == 1) { //didn't contain EQUALS -> split LIKE
            nameValue = filterPart.split(Filter.LIKE);
            wildCard = true;
        }
        if (StringUtils.isNotBlank(nameValue[0]) && StringUtils.isNotBlank(nameValue[1])) {
            String userEntered = nameValue[1].replaceAll("'", "").trim();
            if (wildCard) {
                userEntered = userEntered.replaceAll("%", "").trim();
            }
            if (nameValue[0].strip().startsWith(PROFILE)) {
                if (nameValue[0].trim().endsWith(ESObjectConstants.HRUID) || nameValue[0].trim().endsWith("legacyShortId") ||
                        nameValue[0].trim().endsWith(GUID) || nameValue[0].trim().endsWith(LEGACY_ALT_PID)) {
                    valueQueryBuilder(finalQuery, nameValue[0].trim(), userEntered, wildCard, must);
                } else {
                    try {
                        long start = SystemUtil.getLongFromString(userEntered);
                        //set endDate to midnight of that date
                        String endDate = userEntered + END_OF_DAY;
                        long end = SystemUtil.getLongFromDetailDateString(endDate);
                        rangeQueryBuilder(finalQuery, nameValue[0].trim(), start, end, must);
                    } catch (ParseException e) {
                        valueQueryBuilder(finalQuery, nameValue[0].trim(), userEntered, wildCard, must);
                    }
                }
            } else if (nameValue[0].startsWith(DSM)) {
                valueQueryBuilder(finalQuery, nameValue[0].trim(), userEntered, wildCard, must);
            } else if (nameValue[0].startsWith(DATA)) {
                String[] dataParam = nameValue[0].split("\\.");
                try {
                    long start = SystemUtil.getLongFromString(userEntered);
                    //set endDate to midnight of that date
                    String endDate = userEntered + END_OF_DAY;
                    long end = SystemUtil.getLongFromDetailDateString(endDate);
                    rangeQueryBuilder(finalQuery, dataParam[1].trim(), start, end, must);
                } catch (ParseException e) {
                    //was no date string so go for normal text
                    mustOrSearch(finalQuery, dataParam[1].trim(), userEntered, wildCard, must);
                }
            } else if (nameValue[0].startsWith(ADDRESS)) {
                mustOrSearch(finalQuery, nameValue[0].trim(), userEntered, wildCard, must);
            } else if (nameValue[0].startsWith(INVITATIONS)) {
                String[] invitationParam = nameValue[0].split("\\.");
                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();

                boolean alreadyAdded = false;
                try {
                    long start = SystemUtil.getLongFromString(userEntered);
                    //set endDate to midnight of that date
                    String endDate = userEntered + END_OF_DAY;
                    long end = SystemUtil.getLongFromDetailDateString(endDate);
                    rangeQueryBuilder(queryBuilder, INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim(), start, end, must);
                } catch (Exception e) {
                    if (wildCard) {
                        if (must) {
                            queryBuilder.must(QueryBuilders.wildcardQuery(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim(), userEntered + "*"));
                        } else {
                            queryBuilder.should(QueryBuilders.wildcardQuery(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim(), userEntered + "*"));
                        }
                    } else {
                        if (must) {
                            queryBuilder.must(QueryBuilders.matchQuery(INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim(), userEntered));
                        } else {
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim());
                            alreadyAdded = mustOrSearchActivity(queryBuilder, tmpBuilder, INVITATIONS + DBConstants.ALIAS_DELIMITER + invitationParam[1].trim(), userEntered);
                        }
                    }
                }
                if (!alreadyAdded) {
                    finalQuery.must(queryBuilder);
                }
            } else {
                String[] surveyParam = nameValue[0].split("\\.");
                BoolQueryBuilder activityAnswer = new BoolQueryBuilder();
                BoolQueryBuilder queryBuilder = new BoolQueryBuilder();

                boolean alreadyAdded = false;
                if (CREATED_AT.equals(surveyParam[1].trim()) || COMPLETED_AT.equals(surveyParam[1].trim()) || LAST_UPDATED.equals(surveyParam[1].trim())) {
                    try {
                        //activity dates
                        long start = SystemUtil.getLongFromString(userEntered);
                        //set endDate to midnight of that date
                        String endDate = userEntered + END_OF_DAY;
                        long end = SystemUtil.getLongFromDetailDateString(endDate);
                        rangeQueryBuilder(queryBuilder, ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), start, end, must);
                    } catch (ParseException e) {
                        //activity status
                        valueQueryBuilder(queryBuilder, ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), userEntered, wildCard, must);
                    }
                } else if (STATUS.equals(surveyParam[1].trim())) {
                    if (wildCard) {
                        if (must) {
                            queryBuilder.must(QueryBuilders.wildcardQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), userEntered + "*"));
                        } else {
                            queryBuilder.should(QueryBuilders.wildcardQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), userEntered + "*"));
                        }
                    } else {
                        if (must) {
                            queryBuilder.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), userEntered));
                        } else {
                            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim());
                            alreadyAdded = mustOrSearchActivity(queryBuilder, tmpBuilder, ACTIVITIES + DBConstants.ALIAS_DELIMITER + surveyParam[1].trim(), userEntered);
                        }
                    }
                } else {
                    //activity user entered
                    activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_STABLE_ID, surveyParam[1].trim()));
                    try {
                        SystemUtil.getLongFromString(userEntered);
                        activityAnswer.must(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_DATE, userEntered));
                    } catch (ParseException e) {
                        //was no date string so go for normal text
                        if (wildCard) {
                            if (must) {
                                activityAnswer.must(QueryBuilders.wildcardQuery(ACTIVITIES_QUESTIONS_ANSWER_ANSWER, userEntered + "*"));
                            } else {
                                activityAnswer.should(QueryBuilders.wildcardQuery(ACTIVITIES_QUESTIONS_ANSWER_ANSWER, userEntered + "*"));
                            }
                        } else {
                            if (must) {
                                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                                orAnswers.should(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_ANSWER, userEntered));
                                if (StringUtils.isNotBlank(userEntered) && userEntered.contains(".")) {
                                    String[] tmp = userEntered.split("\\.");
                                    if (tmp != null && tmp.length > 1 && StringUtils.isNotBlank(tmp[0]) && StringUtils.isNotBlank(tmp[1])) {
                                        orAnswers.should(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_GROUPED_OPTIONS + "." + tmp[0], tmp[1]));
                                        orAnswers.should(QueryBuilders.matchQuery(ACTIVITIES_QUESTIONS_ANSWER_NESTED_OPTIONS + "." + tmp[0], tmp[1]));
                                    }
                                }
                                activityAnswer.must(orAnswers);
                            } else {
                                QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, ACTIVITIES_QUESTIONS_ANSWER_ANSWER);
                                alreadyAdded = mustOrSearchActivity(activityAnswer, tmpBuilder, ACTIVITIES_QUESTIONS_ANSWER_ANSWER, userEntered);
                            }
                        }
                    }
                    if (!alreadyAdded) {
                        NestedQueryBuilder queryActivityAnswer = QueryBuilders.nestedQuery(ACTIVITIES_QUESTIONS_ANSWER, activityAnswer, ScoreMode.Avg);
                        queryBuilder.must(queryActivityAnswer);
                    }
                }
                if (!alreadyAdded) {
                    queryBuilder.must(QueryBuilders.matchQuery(ACTIVITIES + DBConstants.ALIAS_DELIMITER + ACTIVITY_CODE, surveyParam[0].trim()).operator(Operator.AND));
                    NestedQueryBuilder query = QueryBuilders.nestedQuery(ACTIVITIES, queryBuilder, ScoreMode.Avg);
                    finalQuery.must(query);
                }
            }
        } else {
            logger.error("one of the following is null: fieldName: " + nameValue[0] + " userEntered: [hidingValueInCasePHI]");
        }
    }

    private static boolean mustOrSearchActivity(@NonNull BoolQueryBuilder queryBuilder, QueryBuilder tmpBuilder, @NonNull String name, @NonNull String value) {
        if (tmpBuilder != null) {
            ((BoolQueryBuilder) tmpBuilder).should(QueryBuilders.matchQuery(name, value));
            return true;
        } else {
            BoolQueryBuilder orAnswers = new BoolQueryBuilder();
            orAnswers.should(QueryBuilders.matchQuery(name, value));
            queryBuilder.must(orAnswers);
        }
        return false;
    }

    private static void mustOrSearch(@NonNull BoolQueryBuilder finalQuery, @NonNull String name, @NonNull String value, boolean wildCard, boolean must) {
        if (must) {
            valueQueryBuilder(finalQuery, name, value, wildCard, must);
        } else {
            QueryBuilder tmpBuilder = findQueryBuilderForFieldName(finalQuery, name);
            if (tmpBuilder != null) {
                ((BoolQueryBuilder) tmpBuilder).should(QueryBuilders.matchQuery(name, value));
            } else {
                BoolQueryBuilder orAnswers = new BoolQueryBuilder();
                orAnswers.should(QueryBuilders.matchQuery(name, value));
                finalQuery.must(orAnswers);
            }
        }
    }

    private static void valueQueryBuilder(@NonNull BoolQueryBuilder finalQuery, @NonNull String name, @NonNull String query, boolean wildCard, boolean must) {
        if (wildCard) {
            if (must) {
                finalQuery.must(QueryBuilders.wildcardQuery(name, "*" + query.toLowerCase() + "*"));
            } else {
                finalQuery.should(QueryBuilders.wildcardQuery(name, "*" + query.toLowerCase() + "*"));
            }
        } else {
            if (must) {
                String fieldType = getFieldTypeByFieldName(name);
                String finalFieldName = getFinalFieldName(name, fieldType);
                finalQuery.must(QueryBuilders.termQuery(finalFieldName, query));
            } else {
                finalQuery.should(QueryBuilders.matchQuery(name, query));
            }
        }
    }

    private static String getFinalFieldName(String fieldName, String fieldType) {
        return fieldType.equals(TEXT) ? new StringBuilder(fieldName).append(KEYWORD).toString() : fieldName;
    }

    private static String getFieldsAsString(String anyStudy) {
        return fieldMappings.get(anyStudy).source().string();
    }

    private static String getAnyStudy() {
        return fieldMappings.keySet().stream().findAny().orElseThrow(() -> new RuntimeException("Error while getting study mapping from ES"));
    }


    private static String getFieldTypeByFieldName(String name) {
        String anyStudy = getAnyStudy();
        String fields = getFieldsAsString(anyStudy);
        String[] fieldsArray = name.split(ESCAPE_CHARACTER_DOT_SEPARATOR);
        String outerField = fieldsArray[OUTER_FIELD_INDEX];
        Gson gson = new Gson();
        HashMap fieldsMap = gson.fromJson(fields, HashMap.class);
        HashMap propertiesMap = getField(gson, fieldsMap, PROPERTIES);
        HashMap finalField = getField(gson, propertiesMap, outerField);
        return isFieldNested(fieldsArray)
                ? (String) getFinalField(fieldsArray, gson, finalField).get(TYPE)
                : (String) finalField.get(TYPE);
    }

    private static HashMap getFinalField(String[] fieldsArray, Gson gson, HashMap finalField) {
        for (String field : Arrays.copyOfRange(fieldsArray, INNER_FIELD_INDEX, fieldsArray.length)) {
            finalField = getFinalFieldDynamically(gson, finalField, field);
        }
        return finalField;
    }

    private static HashMap getFinalFieldDynamically(Gson gson, HashMap finalField, String field) {
        return finalField.containsKey(PROPERTIES)
                ? getPropertiesAndThenField(gson, finalField, field)
                : getField(gson, finalField, field);
    }

    private static HashMap getField(Gson gson, HashMap finalField, String field) {
        return gson.fromJson(String.valueOf(finalField.get(field)), HashMap.class);
    }

    private static HashMap getPropertiesAndThenField(Gson gson, HashMap finalField, String field) {
        finalField = gson.fromJson(String.valueOf(finalField.get(PROPERTIES)), HashMap.class);
        finalField = gson.fromJson(String.valueOf(finalField.get(field)), HashMap.class);
        return finalField;
    }

    private static boolean isFieldNested(String[] fieldsArray) {
        return fieldsArray.length > 1;
    }

    private static void rangeQueryBuilder(@NonNull BoolQueryBuilder finalQuery, @NonNull String name, long start, long end, boolean must) {
        if (must) {
            finalQuery.must(QueryBuilders.rangeQuery(name.trim()).gte(start).lte(end));
        } else {
            finalQuery.should(QueryBuilders.rangeQuery(name.trim()).gte(start).lte(end));
        }
    }

    private static boolean isESUsersIndex(String index) {
        return index.startsWith("users");
    }

    public static Map<String, Map<String, Object>> getESData(@NonNull DDPInstance instance) {
        if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
            return getDDPParticipantsFromES(instance.getName(), instance.getParticipantIndexES());
        }
        return null;
    }
}
