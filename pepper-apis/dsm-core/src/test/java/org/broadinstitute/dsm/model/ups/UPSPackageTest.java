package org.broadinstitute.dsm.model.ups;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class UPSPackageTest {

    UPSActivity labelGenerated =
            new UPSActivity((UPSLocation) null, new UPSStatus("M", "Shipper created a label, UPS has not received the package yet", "MP"),
                    "20200910", "114203", null, null, null);
    UPSActivity delivered =
            new UPSActivity((UPSLocation) null, new UPSStatus("D", "Delivered", "KB"), "20200913", "114203", null, null, null);
    UPSActivity inTransit =
            new UPSActivity((UPSLocation) null, new UPSStatus("I", "Departed from Facility", "MP"), "20200912", "114203", null, null, null);
    UPSActivity pickedUp = new UPSActivity((UPSLocation) null, new UPSStatus("P", "Pickup", "XD"), "20200911", "114203", null, null, null);

    @Test
    public void testEarliestPickup() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(labelGenerated, inTransit, pickedUp, delivered);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(pickedUp.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestIsNullWhenOnlyLabelGenerated() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(labelGenerated);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertNull(upsPackage.getEarliestPackageMovementEvent());
    }

    @Test
    public void testEarliestDelivered() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(delivered, labelGenerated);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(delivered.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestInTransit() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(labelGenerated, inTransit);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(inTransit.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }
}
