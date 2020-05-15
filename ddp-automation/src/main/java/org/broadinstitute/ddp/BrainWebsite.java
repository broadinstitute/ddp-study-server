package org.broadinstitute.ddp;

import com.epam.jdi.uitests.web.settings.WebSettings;

public class BrainWebsite extends DDPWebSite {
    public static void setDomain() {
        WebSettings.domain = CONFIG.getString(ConfigFile.BRAIN_URL);
    }
}
