package org.broadinstitute.dsm.model.ups;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class UPSPackageTest {

    UPSActivity LABEL_GENERATED = new UPSActivity((UPSLocation) null, new UPSStatus("M", "Shipper created a label, UPS has not received the package yet", "MP"), "20200910", "114203", null, null, null);
    UPSActivity DELIVERED = new UPSActivity((UPSLocation) null, new UPSStatus("D", "Delivered", "KB"), "20200913", "114203", null, null, null);
    UPSActivity IN_TRANSIT = new UPSActivity((UPSLocation) null, new UPSStatus("I", "Departed from Facility", "MP"), "20200912", "114203", null, null, null);
    UPSActivity PICKED_UP = new UPSActivity((UPSLocation) null, new UPSStatus("P", "Pickup", "XD"), "20200911", "114203", null, null, null);

    @Test
    public void testEarliestPickup() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED, IN_TRANSIT, PICKED_UP, DELIVERED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(PICKED_UP.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestIsNullWhenOnlyLabelGenerated() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertNull(upsPackage.getEarliestPackageMovementEvent());
    }

    @Test
    public void testEarliestDelivered() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(DELIVERED, LABEL_GENERATED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(DELIVERED.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestInTransit() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED, IN_TRANSIT);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]), null, null, null, null);

        Assert.assertEquals(IN_TRANSIT.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }
}
