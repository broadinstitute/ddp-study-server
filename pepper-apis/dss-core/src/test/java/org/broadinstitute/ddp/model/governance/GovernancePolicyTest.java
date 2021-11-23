package org.broadinstitute.ddp.model.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class GovernancePolicyTest {

    private static final PexInterpreter interpreter = new TreeWalkInterpreter();
    private static final Handle handle = Mockito.mock(Handle.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testShouldCreateGovernedUser() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        assertTrue(policy.shouldCreateGovernedUser(handle, interpreter, "guid"));

        policy = new GovernancePolicy(1, new Expression("false"));
        assertFalse(policy.shouldCreateGovernedUser(handle, interpreter, "guid"));
    }

    @Test
    public void testShouldCreateGovernedUser_errors() {
        thrown.expect(PexException.class);

        var policy = new GovernancePolicy(1, new Expression("not supported"));
        policy.shouldCreateGovernedUser(handle, interpreter, "guid");
    }

    @Test
    public void testGetApplicableAgeOfMajorityRule_noRules() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        var actual = policy.getApplicableAgeOfMajorityRule(handle, interpreter, "guid");
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testGetApplicableAgeOfMajorityRule_noneSuccessful() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        policy.addAgeOfMajorityRule(
                new AgeOfMajorityRule("false", 21, 4),
                new AgeOfMajorityRule("false", 18, 6));

        var actual = policy.getApplicableAgeOfMajorityRule(handle, interpreter, "guid");
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testGetApplicableAgeOfMajorityRule_firstSuccessfulRule() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        policy.addAgeOfMajorityRule(
                new AgeOfMajorityRule("false", 21, 4),
                new AgeOfMajorityRule("true", 19, 5),
                new AgeOfMajorityRule("true", 18, 6));

        var actual = policy.getApplicableAgeOfMajorityRule(handle, interpreter, "guid");
        assertTrue(actual.isPresent());
        assertEquals(19, actual.get().getAge());
    }

    @Test
    public void testGetApplicableAgeOfMajorityRule_errors() {
        thrown.expect(PexException.class);

        var policy = new GovernancePolicy(1, new Expression("true"));
        policy.addAgeOfMajorityRule(
                new AgeOfMajorityRule("false", 21, 4),
                new AgeOfMajorityRule("not supported", 19, 5),
                new AgeOfMajorityRule("true", 18, 6));

        policy.getApplicableAgeOfMajorityRule(handle, interpreter, "guid");
    }

    @Test
    public void testHasReachedAgeOfMajority() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 18, 6));
        var birthDate = LocalDate.of(1982, 12, 13);

        var today = LocalDate.of(2019, 12, 13);
        var actual = policy.hasReachedAgeOfMajority(handle, interpreter, "guid", birthDate, today);
        assertTrue(actual);
    }

    @Test
    public void testHasReachedAgeOfMajority_defaultsToFalse() {
        var policy = new GovernancePolicy(1, new Expression("true"));
        var actual = policy.hasReachedAgeOfMajority(handle, interpreter, "guid", LocalDate.now(), LocalDate.now());
        assertFalse(actual);
    }

    @Test
    public void testHasReachedAgeOfMajority_errors() {
        thrown.expect(PexException.class);

        var policy = new GovernancePolicy(1, new Expression("true"));
        policy.addAgeOfMajorityRule(new AgeOfMajorityRule("not supported", 18, 6));
        policy.hasReachedAgeOfMajority(handle, interpreter, "guid", LocalDate.now(), LocalDate.now());
    }
}
