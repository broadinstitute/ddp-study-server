package org.broadinstitute.ddp.filter;

import org.junit.Assert;
import org.junit.Test;

public class AuthPathRegexUtilTest {

    AuthPathRegexUtil authPathRegexUtil = new AuthPathRegexUtil();

    @Test
    public void testProfilePathMatching() {
        Assert.assertTrue(authPathRegexUtil.isProfileRoute("/pepper/v1/user/blah/profile"));
        Assert.assertTrue(authPathRegexUtil.isProfileRoute("/pepper/v25/user/blah/profile"));
        Assert.assertFalse(authPathRegexUtil.isProfileRoute("/pepper/v1/user/blah/"));
        Assert.assertFalse(authPathRegexUtil.isProfileRoute("/pepper/v1/user/profile"));
        Assert.assertFalse(authPathRegexUtil.isProfileRoute("/pepper/v5/user/blah/"));
    }

    @Test
    public void testUserStudyPathMatching() {
        Assert.assertTrue(authPathRegexUtil.isUserStudyRoute("/pepper/v1/user/blah/studies/yxz/bar/"));
        Assert.assertTrue(authPathRegexUtil.isUserStudyRoute("/pepper/v25/user/blah/studies/xyz/fiz/buz"));
        Assert.assertFalse(authPathRegexUtil.isUserStudyRoute("/pepper/v1/user/blah/"));
        Assert.assertFalse(authPathRegexUtil.isUserStudyRoute("/pepper/v1/user/study"));
        Assert.assertFalse(authPathRegexUtil.isUserStudyRoute("/pepper/v5/user/"));
    }

    @Test
    public void testGovernedUserPathMatching() {
        Assert.assertTrue(authPathRegexUtil.isGovernedParticipantsRoute("/pepper/v1/user/blah/participants"));
        Assert.assertTrue(authPathRegexUtil.isGovernedParticipantsRoute("/pepper/v25/user/blah/participants"));
        Assert.assertFalse(authPathRegexUtil.isGovernedParticipantsRoute("/pepper/v1/user/participants/"));
        Assert.assertFalse(authPathRegexUtil.isGovernedParticipantsRoute("/pepper/v1/user/foo/study/bar"));
        Assert.assertFalse(authPathRegexUtil.isGovernedParticipantsRoute("/pepper/v5/user/"));
    }

    @Test
    public void testAdminPathMatching() {
        Assert.assertTrue(authPathRegexUtil.isAdminRoute("/pepper/v1/admin/blah/"));
        Assert.assertTrue(authPathRegexUtil.isAdminRoute("/pepper/v25/admin/blah/"));
        Assert.assertFalse(authPathRegexUtil.isAdminRoute("/pepper/v1/user/admin/"));
        Assert.assertTrue(authPathRegexUtil.isAdminRoute("/pepper/v5/admin/"));
    }

    @Test
    public void testAutocompletePathMatching() {
        Assert.assertTrue(authPathRegexUtil.isAutocompleteRoute("/pepper/v1/autocomplete/blah/"));
        Assert.assertTrue(authPathRegexUtil.isAutocompleteRoute("/pepper/v1/autocomplete/blah"));
        Assert.assertTrue(authPathRegexUtil.isAutocompleteRoute("/pepper/v1/autocomplete/b/"));
        Assert.assertTrue(authPathRegexUtil.isAutocompleteRoute("/pepper/v1/autocomplete/b"));
        Assert.assertFalse(authPathRegexUtil.isAutocompleteRoute("/pepper/v5/autocomplete/"));
        Assert.assertFalse(authPathRegexUtil.isAutocompleteRoute("/pepper/v5/autocomplete"));
    }

    @Test
    public void testSuggestionPathMatching() {
        Assert.assertTrue(authPathRegexUtil.isDrugSuggestionRoute("/pepper/v1/studies/xyz/suggestions/drugs"));
        Assert.assertTrue(authPathRegexUtil.isDrugSuggestionRoute("/pepper/v1/studies/xyz/suggestions/drugs/"));
        Assert.assertFalse(authPathRegexUtil.isDrugSuggestionRoute("/pepper/v1/study/xyz/suggestions/drugs"));
        Assert.assertFalse(authPathRegexUtil.isDrugSuggestionRoute("/pepper/v1/studies/suggestions/drugs"));

        Assert.assertTrue(authPathRegexUtil.isCancerSuggestionRoute("/pepper/v1/studies/xyz/suggestions/cancers"));
        Assert.assertTrue(authPathRegexUtil.isCancerSuggestionRoute("/pepper/v1/studies/xyz/suggestions/cancers/"));
        Assert.assertFalse(authPathRegexUtil.isCancerSuggestionRoute("/pepper/v1/study/xyz/suggestions/cancers"));
        Assert.assertFalse(authPathRegexUtil.isCancerSuggestionRoute("/pepper/v1/studies/suggestions/cancers"));
        Assert.assertFalse(authPathRegexUtil.isCancerSuggestionRoute("/pepper/v1/studies/xyz/suggestions/cancers1233"));
    }

    @Test
    public void testStudyPathMatching() {
        Assert.assertTrue(authPathRegexUtil.isStudyRoute("/pepper/v1/studies/yxz"));
        Assert.assertTrue(authPathRegexUtil.isStudyRoute("/pepper/v2/studies/xyz/"));
        Assert.assertTrue(authPathRegexUtil.isStudyRoute("/pepper/v2/studies/xyz-abc/"));
        Assert.assertTrue(authPathRegexUtil.isStudyRoute("/pepper/v3/studies/xyz/foo/bar"));
        Assert.assertFalse(authPathRegexUtil.isStudyRoute("/pepper/v1/user/blah/"));
        Assert.assertFalse(authPathRegexUtil.isStudyRoute("/pepper/v1/user/study"));
        Assert.assertFalse(authPathRegexUtil.isStudyRoute("/pepper/v5/user/"));
    }
}
