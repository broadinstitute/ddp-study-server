
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;
import org.junit.Assert;
import org.junit.Test;

public class ActivityAndDdpIdPostFilterStrategyTest {

    StudyPostFilterStrategy<Activities> oldOsteoPostFilterStrategy = new OldOsteoPostFilterStrategy();

    List<Activities> activities = List.of(
            new Activities("ABOUTCHILD", "v1"),
            new Activities("PARENTAL_CONSENT", "v1"),
            new Activities("RELEASE_MINOR", "v1"),
            new Activities("RELEASE_SELF", "v1"),
            new Activities("PREQUAL", "v1"),
            new Activities("ABOUT_YOU", "v1"),
            new Activities("CONSENT", "v1"),
            new Activities("LOVEDONE", "v1"),
            new Activities("CONSENT_ASSENT", "v1")
    );

    @Test
    public void correctActivities() {
        activities.forEach(activity -> Assert.assertTrue(oldOsteoPostFilterStrategy.test(activity)));
    }

    @Test
    public void correctActivitiesWithWrongVersions() {
        activities.stream()
                .map(activity -> new Activities(activity.getActivityCode(), "Anything other than 'v1'"))
                .forEach(activity -> Assert.assertFalse(oldOsteoPostFilterStrategy.test(activity)));
    }

    @Test
    public void wrongActivitiesWithCorrectVersions() {
        activities.stream()
                .map(activity -> new Activities(activity.getActivityCode().concat(randomString(3)), "v1"))
                .forEach(activity -> Assert.assertFalse(oldOsteoPostFilterStrategy.test(activity)));
    }

    @Test
    public void wrongActivitiesWithWrongVersions() {
        activities.stream()
                .map(activity -> new Activities(randomString(3), randomString(3)))
                .forEach(activity -> Assert.assertFalse(oldOsteoPostFilterStrategy.test(activity)));
    }

    public static String randomString(int length) {
        byte[] bytes = new byte[length];
        new java.util.Random().nextBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

}
