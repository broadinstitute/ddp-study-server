package org.broadinstitute.ddp.filter;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

public class StudyLanguageContentTypeSettingFilterTest {
    @Test
    public void test_whenCreateHeaderFromLocaleIsCalled_thenItReturnsCorrectHeader() {
        Locale locale = Locale.forLanguageTag("en-GB");
        String header = StudyLanguageContentTypeSettingFilter.createHeaderFromLocale(locale);
        Assert.assertEquals("en", locale.getLanguage());
        Assert.assertEquals("GB", locale.getCountry());
        Assert.assertEquals("en-GB", header);
    }
}
