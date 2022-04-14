package org.broadinstitute.dsm.shipping;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.model.ups.UPSPackage;
import org.broadinstitute.dsm.model.ups.UPSTrackingResponse;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Not a test, but a handy way to look up
 * shipping history
 */
@Ignore
public class UPSTrackerTest {

    static Config cfg;

    @BeforeClass
    public static void setUp() {
        cfg = ConfigFactory.load();

    }

    @Test
    public void lookupHistory() throws Exception {
        UPSTracker upsTracker = new UPSTracker(cfg.getString(DSMServer.UPS_PATH_TO_ENDPOINT),
                cfg.getString(DSMServer.UPS_PATH_TO_USERNAME),
                cfg.getString(DSMServer.UPS_PATH_TO_PASSWORD),
                cfg.getString(DSMServer.UPS_PATH_TO_ACCESSKEY));


        UPSTrackingResponse response = upsTracker.lookupTrackingInfo("1Z8Y25298492998157");
        UPSPackage upsPackage = response.getTrackResponse().getShipment()[0].getUpsPackageArray()[0];
        System.out.println(upsPackage.printActivity());
    }
}
