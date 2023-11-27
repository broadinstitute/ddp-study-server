package org.broadinstitute.ddp;

import java.io.IOException;

import com.typesafe.config.Config;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicHeader;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.ConfigManager;


public class SmokeTest {

    public static final String HEALTHCHECK = "healthcheck";

    /**
     * Executes smoke test.
     */
    public static void main(String[] args) throws IOException {
        String healthCheckHost = null;
        long timeoutAfter = 30 * 1000;
        if (args != null) {
            if (args.length == 2) {
                healthCheckHost = args[0];
                timeoutAfter = Integer.parseInt(args[1]) * 1000;
            } else {
                System.err.println("Need a host and the number of seconds to wait before timing out.");
                System.exit(-1);
            }
        }

        Config cfg = ConfigManager.getInstance().getConfig();
        String password = cfg.getString(ConfigFile.HEALTHCHECK_PASSWORD);
        String healthCheckUrl = "http://" + healthCheckHost + "/" + HEALTHCHECK;

        long startTime = System.currentTimeMillis();
        boolean healthCheckPassed = false;
        int responseCode = -1;
        System.out.println("Attempting healthcheck at " + healthCheckUrl);
        for (; ; ) {
            try {
                Response response = buildHealthGetRequest(healthCheckUrl, password).execute();
                responseCode = response.returnResponse().getStatusLine().getStatusCode();
                healthCheckPassed = responseCode == 200;
                if (healthCheckPassed) {
                    System.out.println("Smoketest passed");
                    System.exit(0);
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            System.err.println("Reattempting healthcheck...");

            if ((System.currentTimeMillis() - startTime) > timeoutAfter) {
                break;
            }
        }
        if (healthCheckPassed) {
            System.out.println("Smoketest passed");
            System.exit(0);
        } else {
            if (responseCode == -1) {
                System.err.println("Healthcheck failed");
            } else {
                System.err.println("Healthcheck failed.  Server returned " + responseCode);
            }
            System.exit(-1);
        }

    }

    private static Request buildHealthGetRequest(String healthcheckUrl, String password) {
        return Request.Get(healthcheckUrl).addHeader(new BasicHeader("Host", password));
    }

}
