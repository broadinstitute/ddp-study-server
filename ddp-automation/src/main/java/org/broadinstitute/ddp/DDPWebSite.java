package org.broadinstitute.ddp;

import com.epam.jdi.uitests.web.selenium.driver.DriverTypes;
import com.epam.jdi.uitests.web.selenium.elements.WebCascadeInit;
import com.epam.jdi.uitests.web.selenium.elements.composite.WebSite;
import com.epam.jdi.uitests.web.settings.WebSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.TransactionWrapper.DbConfiguration;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DDPWebSite extends WebSite {

    public static Config CONFIG;

    private static final Logger LOG = LoggerFactory.getLogger(DDPWebSite.class);

    static {
        CONFIG = ConfigFactory.load();
        initializeDb();
    }

    public static void init(Class site, boolean useSafari, WebDriver safariDriver) {
        String driverName = null;
        WebCascadeInit webCascadeInit = null;

        if (!WebSettings.getDriverFactory().hasDrivers()) {
            WebSettings.useDriver(DriverTypes.CHROME);
        }

        driverName = WebSettings.getDriverFactory().currentDriverName();
        LOG.info("Website info: {}", site.getSimpleName());
        LOG.info("Driver name info: {}", driverName);
        webCascadeInit.initStaticPages(site, driverName);

        currentSite = site;
    }

    private static void initializeDb() {
        String dbUrl = CONFIG.getString("dbUrl");
        int maxConnections = CONFIG.getInt("maxConnections");
        DbConfiguration configuration = new DbConfiguration(DB.APIS, maxConnections, dbUrl);
        TransactionWrapper.init(configuration);
    }
}
