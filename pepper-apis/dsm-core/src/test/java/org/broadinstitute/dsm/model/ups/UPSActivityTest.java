package org.broadinstitute.dsm.model.ups;

import java.text.SimpleDateFormat;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UPSActivityTest {

    private UPSActivity inTransitActivity;

    private UPSActivity deliveredActivity;

    private UPSActivity labelGenerated = new UPSActivity((String) null, new UPSStatus("M", "Printed some stuff", "MO"), "20201117",
            "015327", null, null, null);

    @Before
    public void setUp() {
        inTransitActivity = new UPSActivity((UPSLocation) null, new UPSStatus("I", "On the way!", "IX"), "20201113", "195327", null, null
                , null);
        deliveredActivity = new UPSActivity((UPSLocation) null, new UPSStatus("D", "You got it!", "DX"), "20201117", "015327", null, null
                , null);
    }


    @Test
    public void testDateParsing() throws Exception {
        String dateString = inTransitActivity.getDate();
        String timeString = inTransitActivity.getTime();
        Instant expectedDate = new SimpleDateFormat("yyyyMMdd kkmmss").parse(dateString + " " + timeString).toInstant();

        Assert.assertEquals(dateString + " " + timeString, inTransitActivity.getDateTimeString());
        Assert.assertEquals(expectedDate, inTransitActivity.getInstant());
    }

    @Test
    public void testInTransit() {
        Assert.assertTrue(inTransitActivity.isOnItsWay());
    }

    @Test
    public void testDelivered() {
        Assert.assertTrue(deliveredActivity.isOnItsWay());
    }

    @Test
    public void testLabelGeneratedIsNotOnItsWay() {
        Assert.assertFalse(labelGenerated.isOnItsWay());
    }
}
