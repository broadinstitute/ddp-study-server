package org.broadinstitute.ddp.model.kit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Set;

import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.Test;

public class KitZipCodeRuleTest {

    private Handle mockHandle;

    @Before
    public void setup() {
        mockHandle = mock(Handle.class);
    }

    @Test
    public void testValidate_badInput() {
        var rule = new KitZipCodeRule(1L, Set.of("12345"));
        assertFalse(rule.validate(mockHandle, null));
        assertFalse(rule.validate(mockHandle, ""));
        assertFalse(rule.validate(mockHandle, "!@#$"));
    }

    @Test
    public void testValidate_goodInput() {
        var rule = new KitZipCodeRule(1L, Set.of("12345", "02115"));
        assertFalse(rule.validate(mockHandle, "02110"));
        assertTrue(rule.validate(mockHandle, "12345"));
        assertTrue(rule.validate(mockHandle, "02115"));
        assertTrue(rule.validate(mockHandle, "  02115  "));
        assertTrue(rule.validate(mockHandle, "  02115  \t"));
    }

    @Test
    public void testValidate_zipExtension() {
        var rule = new KitZipCodeRule(1L, Set.of("12345"));
        assertFalse(rule.validate(mockHandle, "12345-"));
        assertFalse(rule.validate(mockHandle, "12345-1"));
        assertFalse(rule.validate(mockHandle, "12345-12"));
        assertFalse(rule.validate(mockHandle, "12345-123"));
        assertTrue(rule.validate(mockHandle, "12345-1234"));
    }
}
