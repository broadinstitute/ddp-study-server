package org.broadinstitute.ddp.appengine.spark;


import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.jetty.JettyConfig;
import org.broadinstitute.ddp.logging.LogUtil;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.MDC;
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
     *
     * @param stopRouteCallback an optional callback that is called
     *                          when the _ah/stop route is called by GAE
     */
    public static void startSparkServer(StopRouteCallback stopRouteCallback) {
        Config cfg = ConfigManager.getInstance().getConfig();
        String preferredSourceIPHeader = null;
        if (cfg.hasPath(ConfigFile.PREFERRED_SOURCE_IP_HEADER)) {
            preferredSourceIPHeader = cfg.getString(ConfigFile.PREFERRED_SOURCE_IP_HEADER);
        }
        int configFilePort = cfg.getInt(ConfigFile.PORT);
        String appEnginePort = System.getenv(APPENGINE_PORT_ENV_VAR);
        int port = appEnginePort != null ? Integer.parseInt(appEnginePort) : configFilePort;

        int requestThreadTimeoutMillis = 60000;
        if (cfg.hasPath(ConfigFile.THREAD_TIMEOUT)) {
            requestThreadTimeoutMillis = cfg.getInt(ConfigFile.THREAD_TIMEOUT);
        }
        JettyConfig.setupJetty(preferredSourceIPHeader);
        // By default, spark threads spawned by an http request never time out, which can result
        // in crashes under heavy load with long running http requests, so we set a timeout.
        threadPool(-1, -1, requestThreadTimeoutMillis);

        log.info("Starting spark on port {}", port);

        // When running in GAE, it's important to respond to the _ah/start request
        // as quickly as possible so that the GAE scheduler knows the app has started
        // cleanly.  Otherwise, the GAE scheduler may start a runaway process of creating
        // multiple instances as it tries to find one that works.
        Spark.port(port);
        Spark.get(RouteConstants.GAE.START_ENDPOINT, (request, response) -> {
            log.info("Received GAE start request [{}] for instance {} deployment {}", request.url(),
                    MDC.get(LogUtil.GAE_INSTANCE), MDC.get(LogUtil.GAE_DEPLOYMENT_ID));
            response.status(HttpStatus.SC_OK);
            return "";
        });

        Spark.get(RouteConstants.GAE.STOP_ENDPOINT, (request, response) -> {
            log.info("Received GAE stop request [{}] for instance {} deployment {}", request.url(),
                    MDC.get(LogUtil.GAE_INSTANCE), MDC.get(LogUtil.GAE_DEPLOYMENT_ID));
            if (stopRouteCallback != null) {
                stopRouteCallback.onStop();
            }
            response.status(HttpStatus.SC_OK);
            return "";
        });
    }

    /**
     * Called when the {@link org.broadinstitute.ddp.constants.RouteConstants.GAE#STOP_ENDPOINT}
     * is called by the app engine dispatcher
     */
    @FunctionalInterface
    public interface StopRouteCallback {

        void onStop();
    }
}
