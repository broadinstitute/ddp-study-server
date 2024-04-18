package org.broadinstitute.ddp.appengine.spark;


import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.jetty.JettyConfig;
import org.broadinstitute.ddp.util.ConfigManager;
import spark.Spark;

import static spark.Spark.threadPool;

/**
 * Utility for booting Pepper spark apps
 * in app engine.
 */
@Slf4j
public class SparkBootUtil {

    public static final String APPENGINE_PORT_ENV_VAR = "PORT";

    /**
     * Reads configuration and environment settings common to
     * DSM, DSS, and Housekeeping and starts a spark server on the appropriate port.
     * Also registers routes for _ah/start and _ah/stop that log the request and
     * return 200 immediately.
     */
    public static void startSparkServer() {
        Config cfg = ConfigManager.getInstance().getConfig();
        String preferredSourceIPHeader = null;
        if (cfg.hasPath(ConfigFile.PREFERRED_SOURCE_IP_HEADER)) {
            preferredSourceIPHeader = cfg.getString(ConfigFile.PREFERRED_SOURCE_IP_HEADER);
        }
        int configFilePort = cfg.getInt(ConfigFile.PORT);
        String appEnginePort = System.getenv(APPENGINE_PORT_ENV_VAR); // the port defined by app engine "wins"
        int port = -1;
        if (appEnginePort != null) {
            port = Integer.parseInt(appEnginePort);
        } else {
            port = configFilePort;
        }

        int requestThreadTimeout = 60000;
        if (cfg.hasPath(ConfigFile.THREAD_TIMEOUT)) {
            requestThreadTimeout = cfg.getInt(ConfigFile.THREAD_TIMEOUT);
        }
        JettyConfig.setupJetty(preferredSourceIPHeader);
        threadPool(-1, -1, requestThreadTimeout);

        log.info("Starting spark on port {}", port);
        Spark.port(port);
        Spark.get(RouteConstants.GAE.START_ENDPOINT, (request, response) -> {
            log.info("Received GAE start request [{}]", request.url());
            response.status(HttpStatus.SC_OK);
            return "";
        });
        Spark.get(RouteConstants.GAE.STOP_ENDPOINT, (request, response) -> {
            log.info("Received GAE stop request [{}]", request.url());
            response.status(HttpStatus.SC_OK);
            return "";
        });
    }
}
