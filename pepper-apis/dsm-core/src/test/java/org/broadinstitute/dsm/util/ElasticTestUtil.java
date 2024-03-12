package org.broadinstitute.dsm.util;

import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_DETAIL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Address;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainless;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.xcontent.XContentType;
import org.junit.Assert;

@Slf4j
public class ElasticTestUtil {

    /**
     * When creating an index for testing, it's best to start by extracting the mappings
     * and settings files from a known good ES instance and storing these files
     * in resources/elastic
     * @param mappingsFile curl -u -XGET --header 'Content-Type: application/json'
     *                     https://[...].cloud.es.io:9243/participants_structured.[umbrella/study]/_mapping
     * @param settingsFile curl -u -XGET --header 'Content-Type: application/json'
     *      *                     https://[...].cloud.es.io:9243/participants_structured.[umbrella/study]/_settings
     */
    public static String createIndex(String realm, String mappingsFile, String settingsFile) {
        log.info("Creating test index for realm {}", realm);
        String indexName = createIndex(realm);
        try {
            if (StringUtils.isNotBlank(settingsFile)) {
                String settingsJson = TestUtil.readFile(settingsFile);
                updateSettings(indexName, settingsJson);
            }
            String mappingsJson = TestUtil.readFile(mappingsFile);
            updateMapping(indexName, mappingsJson);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception updating index " + realm + ":" + e.getMessage());
        }
        return indexName;
    }

    public static String createIndex(String realm) {
        String indexName = String.format("participants_structured.%s", realm);
        try {
            RestHighLevelClient client = ElasticSearchUtil.getClientInstance();
            CreateIndexRequest req = new CreateIndexRequest(indexName);
            String settingsJson = TestUtil.readFile("elastic/indexSettings.json");
            req.settings(settingsJson, XContentType.JSON);
            client.indices().create(req, RequestOptions.DEFAULT);
            log.info("Created index {}", indexName);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(String.format("Exception creating index %s: %s", indexName, e.getMessage()));
        }
        return indexName;
    }

    public static void deleteIndex(String esIndex) {
        try {
            RestHighLevelClient client = ElasticSearchUtil.getClientInstance();
            DeleteIndexRequest request = new DeleteIndexRequest(esIndex);
            AcknowledgedResponse res = client.indices().delete(request, RequestOptions.DEFAULT);
            log.info("AcknowledgedResponse: {}", res);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception deleting index: " + e);
        }
    }

    /**
     * Updates the mappings for the index.  See {@link #createIndex(String, String, String)}
     * for guidance on how to create a test index.
     */
    public static void updateMapping(String esIndex, String mappingJson) {
        PutMappingRequest putMappingRequest = new PutMappingRequest(esIndex);
        putMappingRequest.source(mappingJson, XContentType.JSON);

        try {
            IndicesClient indicesClient = ElasticSearchUtil.getClientInstance().indices();
            indicesClient.putMapping(putMappingRequest, RequestOptions.DEFAULT);
            ElasticSearchUtil.loadFieldMappings();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(String.format("Unexpected exception updating ES mappings for index %s: %s", esIndex, e));
        }
    }

    /**
     * Updates the mappings for the index.  When creating an index for the first time,
     * query it from an existing elastic instance by running a command like this
     */
    public static void updateSettings(String esIndex, String settingsJson) {
        UpdateSettingsRequest putSettingsRequest = new UpdateSettingsRequest(esIndex);
        putSettingsRequest.settings(settingsJson, XContentType.JSON);

        try {
            IndicesClient indicesClient = ElasticSearchUtil.getClientInstance().indices();
            indicesClient.putSettings(putSettingsRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception updating ES settings for index " + esIndex);
        }
    }

    public static String getParticipantDocumentAsString(String index, String ddpParticipantId) {
        return _getParticipantDocument(index, ddpParticipantId).getSourceAsString();
    }

    public static Map<String, Object> getParticipantDocument(String index, String ddpParticipantId) {
        return _getParticipantDocument(index, ddpParticipantId).getSource();
    }

    private static GetResponse _getParticipantDocument(String index, String ddpParticipantId) {
        GetResponse res = null;
        try {
            RestHighLevelClient client = ElasticSearchUtil.getClientInstance();

            GetRequest getRequest = new GetRequest().index(index).id(ddpParticipantId);
            res = client.get(getRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception getting document for participant " + ddpParticipantId);
        }
        return res;
    }

    public static void addDsmParticipant(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto) {
        ElasticSearchParticipantExporterFactory.fromPayload(
                new ParticipantExportPayload(
                        participantDto.getRequiredParticipantId(),
                        participantDto.getRequiredDdpParticipantId(),
                        ddpInstanceDto.getDdpInstanceId(),
                        ddpInstanceDto.getInstanceName(),
                        ddpInstanceDto
                )
        ).export();
    }

    public static void createParticipant(String esIndex, ParticipantDto participantDto) {
        String ddpParticipantId = participantDto.getRequiredDdpParticipantId();
        try {
            Map<String, Object> props = new HashMap<>();
            props.put("ddpParticipantId", ddpParticipantId);
            props.put("participantId", participantDto.getRequiredParticipantId());
            props.put("ddpInstanceId", participantDto.getDdpInstanceId());
            props.put("created", participantDto.getLastChanged());
            Map<String, Object> parent = new HashMap<>();
            parent.put("participant", props);
            Map<String, Object> source = new HashMap<>();
            source.put("dsm", parent);

            ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, source);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating ES participant " + ddpParticipantId);
        }
    }

    public static void createOncHistoryDetail(String esIndex, OncHistoryDetail oncHistoryDetail, String ddpParticipantId) {
        try {
            Map<String, Object> props = new HashMap<>();
            props.put("oncHistoryDetailId", oncHistoryDetail.getOncHistoryDetailId());
            props.put("ddpInstanceId", oncHistoryDetail.getDdpInstanceId());
            props.put("medicalRecordId", oncHistoryDetail.getMedicalRecordId());
            props.put("phone", oncHistoryDetail.getPhone());
            props.put("fax", oncHistoryDetail.getFax());
            props.put("facility", oncHistoryDetail.getFacility());
            props.put("destructionPolicy", oncHistoryDetail.getDestructionPolicy());
            Map<String, Object> source = new HashMap<>();
            source.put("oncHistoryDetail", props);

            String scriptText = String.format("if (ctx._source.dsm.%s == null) {"
                            + "ctx._source.dsm.%s = new ArrayList(); }"
                            + "ctx._source.dsm.%s.add(params.%s);",
                    ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL);
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            queryBuilder.must(QueryBuilders.termsQuery("_id", ddpParticipantId));

            UpsertPainless upsert = new UpsertPainless(esIndex, queryBuilder);
            upsert.export(scriptText, source, "oncHistoryDetail");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating oncHistoryDetail for participant " + ddpParticipantId);
        }
    }

    public static Profile addParticipantProfileFromFile(String esIndex, String fileName, String ddpParticipantId) {
        Gson gson = new Gson();
        try {
            String profileJson = TestUtil.readFile(fileName);
            Profile profile = gson.fromJson(profileJson, Profile.class);
            profile.setGuid(ddpParticipantId);
            addParticipantProfile(esIndex, profile);
            return profile;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating profile for participant " + ddpParticipantId);
            return null;
        }
    }

    public static Address addParticipantAddressFromFile(String esIndex, String fileName, String ddpParticipantId) {
        Gson gson = new Gson();
        try {
            String json = TestUtil.readFile(fileName);
            Address address = gson.fromJson(json, Address.class);
            addParticipantAddress(esIndex, ddpParticipantId, address);
            return address;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating address for participant " + ddpParticipantId);
            return null;
        }
    }

    /**
     * Add a DSM entity to the participant doc
     *
     * @param dob date of birth to replace in DSM entity
     * @param dateOfMajority date of majority to replace in DSM entity
     */
    public static Dsm addDsmEntityFromFile(String esIndex, String fileName, String ddpParticipantId, String dob,
                                           String dateOfMajority) {
        Gson gson = new Gson();
        try {
            String json = TestUtil.readFile(fileName);
            json = json.replace("<dateOfBirth>", dob);
            if (StringUtils.isNotBlank(dateOfMajority)) {
                json = json.replace("<dateOfMajority>", dateOfMajority);
            } else {
                json = json.replace("\"dateOfMajority\" : \"<dateOfMajority>\",", "");
            }
            Dsm dsm = gson.fromJson(json, Dsm.class);
            addParticipantDsm(esIndex, dsm, ddpParticipantId);
            return dsm;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating dsm for participant " + ddpParticipantId);
            return null;
        }
    }

    public static List<Activities> addActivitiesFromFile(String esIndex, String fileName, String ddpParticipantId) {
        Gson gson = new Gson();
        try {
            String json = TestUtil.readFile(fileName);
            List<Activities> activitiesList = gson.fromJson(json, List.class);
            addParticipantActivities(esIndex, activitiesList, ddpParticipantId);
            return activitiesList;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating activities for participant " + ddpParticipantId);
            return null;
        }
    }

    public static void addParticipantProfile(String esIndex, Profile profile) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> valueMap = mapper.convertValue(profile, Map.class);
        Map<String, Object> profileMap = Map.of("profile", valueMap);
        ElasticSearchUtil.updateRequest(profile.getGuid(), esIndex, profileMap);
    }

    public static void addParticipantAddress(String esIndex, String ddpParticipantId, Address address) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> valueMap = mapper.convertValue(address, Map.class);
        Map<String, Object> addressMap = Map.of("address", valueMap);
        ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, addressMap);
    }

    public static void addParticipantDsm(String esIndex, Dsm dsm, String ddpParticipantId) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> valueMap = mapper.convertValue(dsm, Map.class);
        Map<String, Object> dsmMap = Map.of("dsm", valueMap);
        ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, dsmMap);
    }

    public static void addParticipantActivities(String esIndex, List<Activities> activitiesList,
                                                String ddpParticipantId) {
        ObjectMapper mapper = new ObjectMapper();
        List<Activities> valueMap = mapper.convertValue(activitiesList, List.class);
        Map<String, Object> activitiesMap = Map.of("activities", valueMap);
        ElasticSearchUtil.updateRequest(ddpParticipantId, esIndex, activitiesMap);
    }

    public static void addActivities(String esIndex, String ddpParticipantId, String activitiesJson) {
        ElasticSearchUtil.updateParticipant(ddpParticipantId, esIndex, activitiesJson);
    }

    /**
     * Remove a field from an existing DSM entity for a participant
     */
    public static void removeDsmField(String esIndex, String ddpParticipantId, String field) {
        String script = String.format("ctx._source.dsm.remove('%s')", field);
        UpsertPainless upsert = new UpsertPainless(esIndex, QueryBuilders.termsQuery("_id", ddpParticipantId));
        upsert.export(script, Map.of(), field);
    }
}
