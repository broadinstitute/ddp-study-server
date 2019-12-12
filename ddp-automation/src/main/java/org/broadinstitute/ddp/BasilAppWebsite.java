package org.broadinstitute.ddp;

import com.epam.jdi.uitests.web.settings.WebSettings;

public class BasilAppWebsite extends DDPWebSite {
    public static void setDomain() {
        WebSettings.domain = CONFIG.getString(ConfigFile.BASE_URL) + "/basil-app";
    }
}
