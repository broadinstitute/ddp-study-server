package org.broadinstitute.ddp;

import com.epam.jdi.uitests.web.settings.WebSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioWebsite extends DDPWebSite {
    private static final Logger logger = LoggerFactory.getLogger(AngioWebsite.class);
    private static boolean needToGoPastGatekeeper;

    public static void setDomain() {
        WebSettings.domain = CONFIG.getString(ConfigFile.ANGIO_URL);
        setEnvironmentContainsGatekeeper();
    }

    private static void setEnvironmentContainsGatekeeper() {
        //Only test environment does not have the gatekeeper
        String url = WebSettings.domain;
        boolean containsGatekeeper = CONFIG.getBoolean(ConfigFile.HAS_GATEKEEPER);

        if (containsGatekeeper && containsGatekeeper == true) {
            needToGoPastGatekeeper = true;

        } else {
            needToGoPastGatekeeper = false;
        }

        logger.info("Current environment {} contains gatekeeper: {}", url, needToGoPastGatekeeper);
    }

    public static boolean environmentContainsGatekeeper() {
        return needToGoPastGatekeeper;
    }
}
