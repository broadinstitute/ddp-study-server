package org.broadinstitute.dsm.util;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.PutMappingRequest;
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

    public static void addParticipantProfile(Profile profile, String esIndex) {
        String json = new Gson().toJson(profile);
        ElasticSearchUtil.updateRequest(profile.getGuid(), esIndex, json);
    }
}
