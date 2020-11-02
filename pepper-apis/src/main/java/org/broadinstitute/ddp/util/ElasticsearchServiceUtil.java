package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchServiceUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchServiceUtil.class);
    private static final Map<Integer, RestHighLevelClient> ES_CLIENTS = new HashMap<>();

    public static String getIndexForStudy(Handle handle, StudyDto studyDto, ElasticSearchIndexType elasticSearchIndexType) {
        String type = elasticSearchIndexType.getElasticSearchCompatibleLabel();
        String umbrella = handle.attach(JdbiUmbrellaStudy.class).findUmbrellaGuidForStudyId(studyDto.getId());
        String studyGuid = studyDto.getGuid().toLowerCase();

        if (StringUtils.isEmpty(umbrella) || StringUtils.isEmpty(studyGuid)) {
            throw new IllegalStateException("Could not create ES index for study with id: " + studyDto.getId());
        }

        return String.join(".", type, umbrella, studyGuid);
    }

    public static synchronized RestHighLevelClient getElasticsearchClient(Config cfg) throws MalformedURLException {
        String userName = cfg.getString(ConfigFile.ELASTICSEARCH_USERNAME);
        String password = cfg.getString(ConfigFile.ELASTICSEARCH_PASSWORD);

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(userName, password));

        URL url = new URL(cfg.getString(ConfigFile.ELASTICSEARCH_URL));
        LOG.info("Using Elasticsearch client URL: {}", url);

        String proxy = ConfigUtil.getStrIfPresent(cfg, ConfigFile.ELASTICSEARCH_PROXY);
        final URL proxyUrl = StringUtils.isNotBlank(proxy) ? new URL(proxy) : null;
        if (proxyUrl != null) {
            LOG.info("Using Elasticsearch client proxy URL: {}", proxy);
        }

        int key = Objects.hash(url, proxyUrl, userName);
        RestHighLevelClient esClient = ES_CLIENTS.get(key);
        if (esClient != null) {
            return esClient;
        }

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(url.getHost(), url.getPort(), url.getProtocol()))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    if (proxyUrl != null) {
                        httpClientBuilder.setProxy(new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol()));
                    }
                    return httpClientBuilder;
                })
                .setMaxRetryTimeoutMillis(100000);

        esClient = new RestHighLevelClient(builder);
        ES_CLIENTS.put(key, esClient);
        LOG.info("Created new Elasticsearch client for URL: {}", url);

        return esClient;
    }

    public static synchronized void shutdownElasticsearchClients() throws IOException {
        IOException ex = null;
        for (var esClient : ES_CLIENTS.values()) {
            try {
                esClient.close();
            } catch (IOException e) {
                if (ex == null) {
                    ex = e;
                } else {
                    ex.addSuppressed(e);
                }
            }
        }

        ES_CLIENTS.clear();
        if (ex != null) {
            throw ex;
        }
    }
}
