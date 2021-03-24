package org.broadinstitute.dsm.model.ups;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class UPSPackageTest {

    UPSActivity LABEL_GENERATED = new UPSActivity(new UPSStatus("M","Shipper created a label, UPS has not received the package yet", "MP"), "20200910","114203");
    UPSActivity DELIVERED = new UPSActivity(new UPSStatus("D","Delivered", "KB"), "20200913","114203");
    UPSActivity IN_TRANSIT = new UPSActivity(new UPSStatus("I","Departed from Facility", "MP"), "20200912","114203");
    UPSActivity PICKED_UP = new UPSActivity(new UPSStatus("P","Pickup", "XD"), "20200911","114203");

    @Test
    public void testEarliestPickup() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED, IN_TRANSIT, PICKED_UP, DELIVERED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]));

        Assert.assertEquals(PICKED_UP.getDateTimeString(),upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestIsNullWhenOnlyLabelGenerated() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]));

        Assert.assertNull(upsPackage.getEarliestPackageMovementEvent());
    }

    @Test
    public void testEarliestDelivered() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(DELIVERED,LABEL_GENERATED);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]));

        Assert.assertEquals(DELIVERED.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }

    @Test
    public void testEarliestInTransit() {
        String trackingNumber = "000000000011";
        List<UPSActivity> history = List.of(LABEL_GENERATED, IN_TRANSIT);
        UPSPackage upsPackage = new UPSPackage(trackingNumber, history.toArray(new UPSActivity[0]));

        Assert.assertEquals(IN_TRANSIT.getDateTimeString(), upsPackage.getEarliestPackageMovementEvent().getDateTimeString());
    }
}
