package org.broadinstitute.ddp.model.study;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

public class PasswordPolicyTest {

    @Test
    public void testFromType() {
        var policy = PasswordPolicy.fromType(PasswordPolicy.PolicyType.FAIR, 32);
        assertNotNull(policy);
        assertEquals(PasswordPolicy.PolicyType.FAIR, policy.getType());
        assertEquals(32, policy.getMinLength());
        assertEquals(3, policy.getMinCharClasses());
        assertEquals(3, policy.getCharClasses().size());
        assertNull(policy.getMaxRepeatedChars());
    }

    @Test
    public void testUsesDefaultLength() {
        var policy = PasswordPolicy.none(null);
        assertEquals(PasswordPolicy.PolicyType.NONE.getDefaultMinPasswordLength(), policy.getMinLength());
        policy = PasswordPolicy.low(null);
        assertEquals(PasswordPolicy.PolicyType.LOW.getDefaultMinPasswordLength(), policy.getMinLength());
        policy = PasswordPolicy.fair(null);
        assertEquals(PasswordPolicy.PolicyType.FAIR.getDefaultMinPasswordLength(), policy.getMinLength());
        policy = PasswordPolicy.good(null);
        assertEquals(PasswordPolicy.PolicyType.GOOD.getDefaultMinPasswordLength(), policy.getMinLength());
        policy = PasswordPolicy.excellent(null);
        assertEquals(PasswordPolicy.PolicyType.EXCELLENT.getDefaultMinPasswordLength(), policy.getMinLength());
    }

    @Test
    public void testCheckPassword_checkLength() {
        var policy = PasswordPolicy.none(100);
        assertFalse(policy.checkPassword("foobar"));
        assertTrue(policy.checkPassword(StringUtils.repeat("1234567890", 12)));
    }

    @Test
    public void testCheckPassword_checkCharClasses() {
        var policy = PasswordPolicy.excellent(5);
        assertFalse(policy.checkPassword("foobar"));
        assertFalse(policy.checkPassword("FooBar"));
        assertTrue(policy.checkPassword("FooBar123"));
        assertTrue(policy.checkPassword("FooBar123!@#"));
    }

    @Test
    public void testCheckPassword_checkRepeats() {
        var policy = PasswordPolicy.excellent(5);
        assertTrue(policy.checkPassword("Foobar123!@#"));
        assertFalse(policy.checkPassword("Foooooobar123!@#"));
    }
}
