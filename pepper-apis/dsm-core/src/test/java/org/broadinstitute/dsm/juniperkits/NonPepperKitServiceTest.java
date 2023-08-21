package org.broadinstitute.dsm.juniperkits;

import static junit.framework.TestCase.assertEquals;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
import org.junit.Assert;
import org.junit.Test;

public class NonPepperKitServiceTest {
    NonPepperStatusKitService nonPepperStatusKitService = new NonPepperStatusKitService();

    @Test
    public void getUserEmailForFieldsTest() {
        UserDto userDto1 = new UserDto(1, "user1", "user1Email", null, 1);
        UserDto userDto2 = new UserDto(2, "user2", "user2Email", null, 1);
        UserDto userDto3 = new UserDto(3, "user3", "user3Email", null, 1);
        UserDto userDto4 = new UserDto(4, "user4", "user4Email", null, 1);
        UserDto userDto5 = new UserDto(5, "user5", "user5Email", null, 1);

        HashMap<Integer, UserDto> map = new HashMap<>();
        map.put(1, userDto1);
        map.put(2, userDto2);
        map.put(3, userDto3);
        map.put(4, userDto4);
        map.put(5, userDto5);

        String user1TestEmail = nonPepperStatusKitService.getUserEmailForFields("1", map);
        Assert.assertEquals("user1Email", user1TestEmail);

        String userNewTestEmail = nonPepperStatusKitService.getUserEmailForFields("user6Email", map);
        Assert.assertEquals("user6Email", userNewTestEmail);

        String user7TestEmail = nonPepperStatusKitService.getUserEmailForFields("7", map);
        Assert.assertEquals("DSM User", user7TestEmail);

        Assert.assertEquals( "", nonPepperStatusKitService.getUserEmailForFields(null, map));
    }

    @Test
    public void convertTimeStringIntoTimeStampTest() {
        Long now = System.currentTimeMillis();
        String timeStamp = nonPepperStatusKitService.convertTimeStringIntoTimeStamp(now);
        Assert.assertNotNull(timeStamp);

        Instant instant = Instant.ofEpochMilli(now).atZone(ZoneId.of("UTC")).toInstant();
        assertEquals(instant.toString(), timeStamp);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        //To make strict date format validation
        formatter.setLenient(false);
        Date parsedDate = null;
        try {
            parsedDate = formatter.parse(timeStamp);
            System.out.println("++validated DATE TIME ++" + formatter.format(parsedDate));
            Assert.assertNotNull(parsedDate);
            Assert.assertEquals(parsedDate.toInstant().atZone(ZoneId.of("UTC")).toInstant(), instant);
        } catch (ParseException e) {
            Assert.fail();
        }

        Long nullValue = null;
        timeStamp = nonPepperStatusKitService.convertTimeStringIntoTimeStamp(nullValue);
        Assert.assertNull(timeStamp);
    }
}
