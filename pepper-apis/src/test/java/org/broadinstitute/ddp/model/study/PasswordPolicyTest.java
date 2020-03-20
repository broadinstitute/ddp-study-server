package org.broadinstitute.ddp.model.study;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PasswordPolicyTest {

    @Test
    public void testFromType() {
        var policy = PasswordPolicy.fromType(PasswordPolicy.PolicyType.FAIR, 32);
        assertNotNull(policy);
        assertEquals(PasswordPolicy.PolicyType.FAIR, policy.getType());
        assertEquals(32, policy.getMinLength());
        assertEquals((Integer) 3, policy.getMinCharClasses());
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
}
