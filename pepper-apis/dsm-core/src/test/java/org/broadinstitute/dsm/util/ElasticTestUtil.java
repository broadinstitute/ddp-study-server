package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
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
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainless;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
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
            CreateIndexResponse createIndexResponse = client.indices().create(req, RequestOptions.DEFAULT);
            log.info("CreateIndexResponse: {}", createIndexResponse);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception creating index " + realm + ":" + e.getMessage());
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

    public static void addInstitutionAndMedicalRecord(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto) {
        String ddpParticipantId = participantDto.getDdpParticipantIdOrThrow();
        Institution institution = new Institution(String.format("%s_GUID", ddpParticipantId), "PHYSICIAN");
        String lastUpdated = Long.toString(System.currentTimeMillis());

        InstitutionRequest institutionRequest = new InstitutionRequest(1L, ddpParticipantId, List.of(institution), lastUpdated);
        inTransaction(conn -> {
            DDPMedicalRecordDataRequest.writeParticipantIntoDb(conn, ddpInstanceDto.getDdpInstanceId().toString(),
                    institutionRequest, ddpInstanceDto.getInstanceName());
            return null;
        });
    }

    public static void addDsmParticipant(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto) {
        ElasticSearchParticipantExporterFactory.fromPayload(
                new ParticipantExportPayload(
                        participantDto.getParticipantIdOrThrow(),
                        participantDto.getDdpParticipantId().orElseThrow(),
                        ddpInstanceDto.getDdpInstanceId().toString(),
                        ddpInstanceDto.getInstanceName(),
                        ddpInstanceDto
                )
        ).export();
    }

    public static void createParticipant(String esIndex, ParticipantDto participantDto) {
        String ddpParticipantId = participantDto.getDdpParticipantIdOrThrow();
        try {
            Map<String, Object> props = new HashMap<>();
            props.put("ddpParticipantId", ddpParticipantId);
            props.put("participantId", participantDto.getParticipantIdOrThrow());
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

    public static void addParticipantProfile(String esIndex, Profile profile) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> valueMap = mapper.convertValue(profile, Map.class);
        Map<String, Object> profileMap = Map.of("profile", valueMap);
        ElasticSearchUtil.updateRequest(profile.getGuid(), esIndex, profileMap);
    }

    public static void addActivities(String esIndex, String ddpParticipantId, String activitiesJson) {
        ElasticSearchUtil.updateParticipant(ddpParticipantId, esIndex, activitiesJson);
    }
}
