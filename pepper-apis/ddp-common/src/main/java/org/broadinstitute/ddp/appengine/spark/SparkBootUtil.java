package org.broadinstitute.ddp.appengine.spark;


import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.jetty.JettyConfig;
import org.broadinstitute.ddp.logging.LogUtil;
import spark.Spark;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static spark.Spark.threadPool;

/**
 * Utility for booting Pepper spark apps
 * in app engine.
 */
@Slf4j
public class SparkBootUtil {

    public static final String APPENGINE_PORT_ENV_VAR = "PORT";

    private static int numShutdownAttempts = 0;

    private static final ScheduledThreadPoolExecutor delayedShutdownExecutor = new ScheduledThreadPoolExecutor(1);

    /**
     * Reads configuration and environment settings common to
     * DSM, DSS, and Housekeeping and starts a spark server on the appropriate port.
     * Also registers routes for _ah/start and _ah/stop that log the request and
     * return 200 immediately.
     *
     * @param stopRouteCallback an optional callback that is called
     *                          when the _ah/stop route is called by GAE
     * @param cfg the config to use
     */
    public static void startSparkServer(AppEngineShutdown stopRouteCallback, Config cfg) {
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
                    System.getenv(LogUtil.GAE_INSTANCE), System.getenv(LogUtil.GAE_DEPLOYMENT_ID));
            response.status(HttpStatus.SC_OK);
            return "";
        });

        Spark.get(RouteConstants.GAE.STOP_ENDPOINT, (request, response) -> {
            log.info("Received GAE stop request [{}] for instance {} deployment {}", request.url(),
                    System.getenv(LogUtil.GAE_INSTANCE), System.getenv(LogUtil.GAE_DEPLOYMENT_ID));
            try {
                if (stopRouteCallback != null) {
                    // Handling the shutdown command will likely shut down spark
                    // itself, so give spark a moment to respond to the current request
                    // before turning it off.  Otherwise, appengine may see the shutdown command
                    // as a failure
                    if (numShutdownAttempts == 0) {
                        delayedShutdownExecutor.schedule(() -> stopRouteCallback.onAhStop(), 500, TimeUnit.MILLISECONDS);
                    } else {
                        log.info("Ignoring shutdown attempt {}", numShutdownAttempts);
                    }
                    numShutdownAttempts++;
                }
            } catch (Exception e) {
                log.error("Error during {}", request.url(), e);
            }
            response.status(HttpStatus.SC_OK);
            log.info("Completing GAE stop request [{}] for instance {} deployment {}", request.url(),
                    System.getenv(LogUtil.GAE_INSTANCE), System.getenv(LogUtil.GAE_DEPLOYMENT_ID));
            return "";
        });

        if (stopRouteCallback != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopRouteCallback.onTerminate()));
        }
    }

    /**
     * Methods called in response to different lifecycle events
     * related to shutting down
     */
    public interface AppEngineShutdown {

        /**
         * Called when app engine _ah/stop route is called.
         * AppEngine may call this repeatedly and give you
         * more time to respond than {@link #onTerminate()}
         */
        void onAhStop();

        /**
         * Called when app engine forcibly stops
         * the VM with a termination signal.  This is
         * the last chance to do any cleanup
         */
        void onTerminate();
    }

}
