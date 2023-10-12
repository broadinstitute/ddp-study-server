package org.broadinstitute.dsm.util;

import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@Slf4j
public class ElasticTestContainer {

    private static final String ELASTIC_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:7.17.10";
    private static ElasticsearchContainer container;
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) {
            log.warn("ElasticTestContainer already initialized");
            return;
        }

        ConfigManager configManager = ConfigManager.getInstance();
        Config cfg = configManager.getConfig();
        Boolean useDisposableTestDbs = ConfigUtil.getBoolIfPresent(cfg, ConfigFile.USE_DISPOSABLE_TEST_DB);
        if (useDisposableTestDbs != null && !useDisposableTestDbs) {
            log.info("Using external ElasticSearch instance (not using ElasticSearch test container)");
            // do this here to avoid DSMConfig initialization issues
            ElasticSearchUtil.initClient(cfg.getString(ConfigFile.ELASTICSEARCH_URL),
                    cfg.getString(ConfigFile.ELASTICSEARCH_USERNAME),
                    cfg.getString(ConfigFile.ELASTICSEARCH_PASSWORD), null);
            initialized = true;
            return;
        }
        log.info("Using ElasticSearch test container");

        // Note: for this test-only container, we do not enable security since it adds an unnecessary layer of complexity
        container = new ElasticsearchContainer(ELASTIC_IMAGE)
                .withExposedPorts(9200)
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node");

        Runnable containerShutdown = () -> {
            log.info("Shutting down ES test container");
            if (container != null) {
                try {
                    container.stop();
                } catch (Exception e) {
                    log.error("Error shutting down ES test  container", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(containerShutdown));

        try {
            log.info("Starting Elastic test container...");
            container.start();
            initialized = true;
            log.info("Elastic test container started");

            String url = String.format("https://%s", container.getHttpHostAddress());
            log.info("ES container url {}", url);

            Map<String, String> keyValues = new HashMap<>();
            keyValues.put(ApplicationConfigConstants.ES_URL, url);
            keyValues.put(ApplicationConfigConstants.ES_USERNAME, "");
            keyValues.put(ApplicationConfigConstants.ES_PASSWORD, "");
            rewriteConfigValues(keyValues, configManager);
            ElasticSearchUtil.initClient(url, "", "", null);
        } catch (Exception e) {
            throw new DsmInternalError("Error starting ES test container", e);
        }
    }

    protected static void rewriteConfigValues(Map<String, String> keyValues, ConfigManager configManager) {
        for (var entry : keyValues.entrySet()) {
            try {
                configManager.overrideValue(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                throw new DsmInternalError("Could not rewrite config with key " + entry.getKey(), e);
            }
        }
    }
}
