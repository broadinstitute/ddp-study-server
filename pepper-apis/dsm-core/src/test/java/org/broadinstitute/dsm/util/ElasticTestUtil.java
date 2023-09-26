package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainless;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
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

    public static String createIndexWithMappings(String realm, String mappingsFile) {
        String indexName = createIndex(realm);
        try {
            String mappingsJson = TestUtil.readFile(mappingsFile);
            updateMapping(indexName, mappingsJson);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception reading mappings: " + e);
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
            Assert.fail("Unexpected exception creating index: " + e);
        }
        return indexName;
    }

    public static void updateMapping(String esIndex, String mappingJson) {
        PutMappingRequest putMappingRequest = new PutMappingRequest(esIndex);
        putMappingRequest.source(mappingJson, XContentType.JSON);
        try {
            ElasticSearchUtil.getClientInstance().indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception updating ES mappings for index " + esIndex);
        }
    }

    public static String getParticipantDocument(String index, String ddpParticipantId) {
        GetResponse res = null;
        try {
            RestHighLevelClient client = ElasticSearchUtil.getClientInstance();

            GetRequest getRequest = new GetRequest().index(index).type("_doc").id(ddpParticipantId);
            res = client.get(getRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception getting document for participant " + ddpParticipantId);
        }
        return res.getSourceAsString();
    }

    public static void addParticipantAndInstitution(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto) {
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

    public static void addProperty(String ddpParticipantId, String propertyPath, String index) {

        String scriptText = String.format("if (ctx._source.%s == null) {"
                        + "ctx._source.%s = new ArrayList(); }",
                propertyPath, propertyPath);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        queryBuilder.must(QueryBuilders.termsQuery("_id", ddpParticipantId));

        try {
            UpsertPainless upsert = new UpsertPainless(null, index, null, queryBuilder);
            upsert.export(scriptText, Collections.emptyMap(), "(none)");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception adding property for participant " + ddpParticipantId);
        }
    }

    public static void addParticipantProfile(Profile profile, String esIndex) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> valueMap = mapper.convertValue(profile, Map.class);
        Map<String, Object> profileMap = Map.of("profile", valueMap);
        ElasticSearchUtil.updateRequest(profile.getGuid(), esIndex, profileMap);
    }

    public static void addActivities(String ddpParticipantId, String activitiesJson, String esIndex) {
        ElasticSearchUtil.updateParticipant(ddpParticipantId, esIndex, activitiesJson);
    }
}
