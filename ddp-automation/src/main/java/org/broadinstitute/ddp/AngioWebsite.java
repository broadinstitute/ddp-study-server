package org.broadinstitute.ddp;

import com.epam.jdi.uitests.web.settings.WebSettings;

public class AngioWebsite extends DDPWebSite {
    public static void setDomain() {
        WebSettings.domain = CONFIG.getString(ConfigFile.ANGIO_URL);
    }
}
