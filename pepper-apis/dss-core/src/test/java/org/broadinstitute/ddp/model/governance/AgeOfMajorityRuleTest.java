package org.broadinstitute.ddp.model.governance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class AgeOfMajorityRuleTest {

    @Test
    public void testGetDateOfMajority() {
        var rule = new AgeOfMajorityRule("true", 21, 4);
        var expected = LocalDate.of(2008, 3, 14);
        var actual = rule.getDateOfMajority(LocalDate.of(1987, 3, 14));
        assertEquals(expected, actual);
    }

    @Test
    public void testGetMajorityPrepDate() {
        var rule = new AgeOfMajorityRule("true", 19, 4);
        var expected = LocalDate.of(2005, 11, 14);
        var actual = rule.getMajorityPrepDate(LocalDate.of(1987, 3, 14));
        assertTrue(actual.isPresent());
        assertEquals(expected, actual.get());
    }

    @Test
    public void testGetMajorityPrepDate_none() {
        var rule = new AgeOfMajorityRule("true", 19, null);
        assertTrue(rule.getMajorityPrepDate(LocalDate.of(1987, 3, 14)).isEmpty());
    }

    @Test
    public void testHasReachedAgeOfMajority() {
        var rule = new AgeOfMajorityRule("true", 18, null);
        var birthDate = LocalDate.of(1982, 12, 13);

        var today = LocalDate.of(1992, 12, 13);
        var actual = rule.hasReachedAgeOfMajority(birthDate, today);
        assertFalse(actual);

        today = LocalDate.of(2000, 12, 13);
        actual = rule.hasReachedAgeOfMajority(birthDate, today);
        assertTrue(actual);

        today = LocalDate.of(2020, 12, 13);
        actual = rule.hasReachedAgeOfMajority(birthDate, today);
        assertTrue(actual);
    }

    @Test
    public void testHasReachedAgeOfMajorityPrep() {
        var rule = new AgeOfMajorityRule("true", 18, 4);
        var birthDate = LocalDate.of(1982, 12, 13);

        var today = LocalDate.of(1992, 12, 13);
        var actual = rule.hasReachedAgeOfMajorityPrep(birthDate, today).get();
        assertFalse(actual);

        today = LocalDate.of(2000, 8, 13);
        actual = rule.hasReachedAgeOfMajorityPrep(birthDate, today).get();
        assertTrue(actual);

        today = LocalDate.of(2020, 8, 13);
        actual = rule.hasReachedAgeOfMajorityPrep(birthDate, today).get();
        assertTrue(actual);
    }

    @Test
    public void testHasReachedAgeOfMajorityPrep_none() {
        var rule = new AgeOfMajorityRule("true", 19, null);
        assertTrue(rule.hasReachedAgeOfMajorityPrep(LocalDate.now(), LocalDate.now()).isEmpty());
    }
}
