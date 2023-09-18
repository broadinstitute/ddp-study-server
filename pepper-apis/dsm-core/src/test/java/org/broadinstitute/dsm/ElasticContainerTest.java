package org.broadinstitute.dsm;

import java.net.MalformedURLException;
import java.net.URL;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.xcontent.XContentType;
import org.junit.Assert;
import org.junit.Test;


@Slf4j
public class ElasticContainerTest extends ElasticBaseTest {

    @Test
    public void addParticipantTest() {
        Config cfg = ConfigManager.getInstance().getConfig();
        String url = cfg.getString("elasticSearch.url");
        String user = cfg.getString("elasticSearch.username");

        log.info("Getting ES client with URL {}, user {}", url, user);
        try (RestHighLevelClient client = ElasticSearchUtil.getClientForElasticsearchCloud(url, user, null, null)) {

            String index = "participants_structured.cmi.cmi-lms";
            String mappingsJson = TestUtil.readFile("elastic/lmsMappings.json");

            CreateIndexRequest req = new CreateIndexRequest(index);
            //req.mapping(mappingsJson, XContentType.JSON);
            CreateIndexResponse createIndexResponse = client.indices().create(req, RequestOptions.DEFAULT);
            log.info("CreateIndexResponse: {}", createIndexResponse);

            String profileJson = TestUtil.readFile("elastic/participantProfile.json");
            String ddpParticipantId = "ElasticContainerTest_ptp";

            UpdateRequest updateRequest = new UpdateRequest().index(index).id(ddpParticipantId).doc(profileJson, XContentType.JSON).docAsUpsert(true);
            UpdateResponse updateResponse = client.update(updateRequest, RequestOptions.DEFAULT);
            log.info("UpdateResponse: {}", updateResponse);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
    }

    private RestHighLevelClient getClient(String url) throws MalformedURLException {
        URL parsedUrl = new URL(url);

        return new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(parsedUrl.getHost(), parsedUrl.getPort(), "http")));
    }
}
