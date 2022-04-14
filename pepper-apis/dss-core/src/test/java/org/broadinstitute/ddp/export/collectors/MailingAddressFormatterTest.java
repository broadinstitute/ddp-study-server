package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.junit.Before;
import org.junit.Test;

public class MailingAddressFormatterTest {

    private MailingAddressFormatter fmt;

    @Before
    public void setup() {
        fmt = new MailingAddressFormatter();
    }

    @Test
    public void testMappings_propertyPerAddressField() {
        Map<String, Object> actual = fmt.mappings();

        assertNotNull(actual);
        assertEquals(10, actual.size());

        assertTrue(actual.containsKey("ADDRESS_FULLNAME"));
        assertEquals("text", ((Map) actual.get("ADDRESS_FULLNAME")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_STREET1"));
        assertEquals("text", ((Map) actual.get("ADDRESS_STREET1")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_STREET2"));
        assertEquals("text", ((Map) actual.get("ADDRESS_STREET2")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_CITY"));
        assertEquals("text", ((Map) actual.get("ADDRESS_CITY")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_STATE"));
        assertEquals("text", ((Map) actual.get("ADDRESS_STATE")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_ZIP"));
        assertEquals("text", ((Map) actual.get("ADDRESS_ZIP")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_COUNTRY"));
        assertEquals("keyword", ((Map) actual.get("ADDRESS_COUNTRY")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_PHONE"));
        assertEquals("text", ((Map) actual.get("ADDRESS_PHONE")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_PLUSCODE"));
        assertEquals("keyword", ((Map) actual.get("ADDRESS_PLUSCODE")).get("type"));
        assertTrue(actual.containsKey("ADDRESS_STATUS"));
        assertEquals("keyword", ((Map) actual.get("ADDRESS_STATUS")).get("type"));
    }

    @Test
    public void testHeaders_columnPerAddressField() {
        List<String> actual = fmt.headers();

        assertNotNull(actual);
        assertEquals(10, actual.size());
        assertEquals("ADDRESS_FULLNAME", actual.get(0));
        assertEquals("ADDRESS_STREET1", actual.get(1));
        assertEquals("ADDRESS_STREET2", actual.get(2));
        assertEquals("ADDRESS_CITY", actual.get(3));
        assertEquals("ADDRESS_STATE", actual.get(4));
        assertEquals("ADDRESS_ZIP", actual.get(5));
        assertEquals("ADDRESS_COUNTRY", actual.get(6));
        assertEquals("ADDRESS_PHONE", actual.get(7));
        assertEquals("ADDRESS_PLUSCODE", actual.get(8));
        assertEquals("ADDRESS_STATUS", actual.get(9));
    }

    @Test
    public void testCollect_noAddress() {
        Map<String, String> actual = fmt.collect(null);

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testCollect_blankAddress() {
        Map<String, String> actual = fmt.collect(new MailAddress());

        assertNotNull(actual);
        assertEquals(10, actual.size());
        assertEquals("", actual.get("ADDRESS_FULLNAME"));
        assertEquals("", actual.get("ADDRESS_STREET1"));
        assertEquals("", actual.get("ADDRESS_STREET2"));
        assertEquals("", actual.get("ADDRESS_CITY"));
        assertEquals("", actual.get("ADDRESS_STATE"));
        assertEquals("", actual.get("ADDRESS_ZIP"));
        assertEquals("", actual.get("ADDRESS_COUNTRY"));
        assertEquals("", actual.get("ADDRESS_PHONE"));
        assertEquals("", actual.get("ADDRESS_PLUSCODE"));
        assertEquals("", actual.get("ADDRESS_STATUS"));
    }

    @Test
    public void testCollect_partialAddress() {
        MailAddress address = new MailAddress("Foo Bar", "85 Main St", null, "Some Town", "", "US", "02115",
                null, null, "description", DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, true);
        Map<String, String> actual = fmt.collect(address);

        assertNotNull(actual);
        assertEquals(10, actual.size());
        assertEquals("Foo Bar", actual.get("ADDRESS_FULLNAME"));
        assertEquals("85 Main St", actual.get("ADDRESS_STREET1"));
        assertEquals("", actual.get("ADDRESS_STREET2"));
        assertEquals("Some Town", actual.get("ADDRESS_CITY"));
        assertEquals("", actual.get("ADDRESS_STATE"));
        assertEquals("02115", actual.get("ADDRESS_ZIP"));
        assertEquals("US", actual.get("ADDRESS_COUNTRY"));
        assertEquals("", actual.get("ADDRESS_PHONE"));
        assertEquals("", actual.get("ADDRESS_PLUSCODE"));
        assertEquals("INVALID", actual.get("ADDRESS_STATUS"));
    }

    @Test
    public void testCollect_fullAddress() {
        MailAddress address = new MailAddress("Foo Bar", "85 Main St", "Apt 2", "Some Town", "MA", "US", "02115",
                "6171112233", "87JC9W76+5G", "description", DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS, true);
        Map<String, String> actual = fmt.collect(address);

        assertNotNull(actual);
        assertEquals(10, actual.size());
        assertEquals("Foo Bar", actual.get("ADDRESS_FULLNAME"));
        assertEquals("85 Main St", actual.get("ADDRESS_STREET1"));
        assertEquals("Apt 2", actual.get("ADDRESS_STREET2"));
        assertEquals("Some Town", actual.get("ADDRESS_CITY"));
        assertEquals("MA", actual.get("ADDRESS_STATE"));
        assertEquals("02115", actual.get("ADDRESS_ZIP"));
        assertEquals("US", actual.get("ADDRESS_COUNTRY"));
        assertEquals("6171112233", actual.get("ADDRESS_PHONE"));
        assertEquals("87JC9W76+5G", actual.get("ADDRESS_PLUSCODE"));
        assertEquals("INVALID", actual.get("ADDRESS_STATUS"));
    }
}
