package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.json.UserActivity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class I18nUtilTest {

    private static UserActivity ua1;
    private static UserActivity ua2;
    private static UserActivity ua3;
    private static UserActivity ua4;
    private static UserActivity ua5;

    @BeforeClass
    public static void setupClass() {
        ua1 = new UserActivity("GUID1", "Test activity instance 1", "subtitle", "FORM", "CREATED", "en");
        ua2 = new UserActivity("GUID1", "Тестовая активити 1", "subtitle", "FORM", "CREATED", "ru");
        ua3 = new UserActivity("GUID2", "Test activity instance 2", "subtitle", "FORM", "CREATED", "en");
        ua4 = new UserActivity("GUID2", "Тестовая активити 2", "subtitle", "FORM", "CREATED", "ru");
        ua5 = new UserActivity("GUID1", "Activité de test 1", "subtitle", "FORM", "CREATED", "fr");
    }

    @Test
    public void test_GetActivityInstanceTranslation_ForOneInstance_TranslationInPreferredLanguageIsReturned() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(Arrays.asList(ua1, ua2), "ru");
        Assert.assertFalse(activityInstanceTrans.isEmpty());
        Assert.assertEquals(1, activityInstanceTrans.size());
        Assert.assertEquals("ru", activityInstanceTrans.get(0).getIsoLanguageCode());
    }

    @Test
    public void test_GetActivityInstanceTranslation_ForManyInstances_TranslationsInPreferredLanguageAreReturned() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(Arrays.asList(ua1, ua2, ua3, ua4), "ru");
        Assert.assertEquals(2, activityInstanceTrans.size());
        List<UserActivity> activityInstanceInRussian = activityInstanceTrans.stream().filter(
                ai -> ai.getIsoLanguageCode().equals("ru")
        ).collect(Collectors.toList());
        Assert.assertEquals(2, activityInstanceInRussian.size());
    }

    @Test
    public void test_GetActivityInstanceTranslation_ForZeroInstances_NoTranslationsReturned() {
        Collection<UserActivity> activityInstances = Collections.emptyList();
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(activityInstances, "ru");
        Assert.assertTrue(activityInstanceTrans.isEmpty());
    }

    @Test
    public void test_GetActivityInstanceTranslation_ForOneInstance_FallbackToEnglishIsPerformed() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(Arrays.asList(ua1, ua5), "ru");
        Assert.assertEquals("en", activityInstanceTrans.get(0).getIsoLanguageCode());
    }

    @Test
    public void test_GetActivityInstanceTranslation_ForOneInstance_FallbackToSecondaryLanguageIsPerformed() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(Arrays.asList(ua5), "ru");
        Assert.assertEquals("fr", activityInstanceTrans.get(0).getIsoLanguageCode());
    }

    @Test(expected = NullPointerException.class)
    public void test_GetActivityInstanceTranslation_WhenSummaryListIsNull_NPEIsThrown() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(null, "en");
    }

    @Test(expected = NullPointerException.class)
    public void test_GetActivityInstanceTranslation_WhenSummaryListContainsNullItems_NPEIsThrown() {
        List<UserActivity> activityInstanceTrans = I18nUtil.getActivityInstanceTranslation(Arrays.asList(null), "en");
    }

    @Test
    public void test_defaultLocal_enIsDefaultLocale() {
        Locale defaultLocale = I18nUtil.DEFAULT_LOCALE;
        Assert.assertEquals(Locale.ENGLISH, defaultLocale);
    }

    @Test
    public void test_resolvePreferredLanguage_nullPreferredLanguageIsAllowed() {
        Locale testLocale = Locale.ROOT;
        Set<Locale> supportedLocales = Collections.singleton(testLocale);
        List<LanguageRange> acceptedLanguages = Arrays.asList(new LanguageRange(testLocale.toLanguageTag()));
        
        I18nUtil.resolvePreferredLanguage(null, testLocale, acceptedLanguages, supportedLocales);
    }

    @Test(expected = NullPointerException.class)
    public void test_resolvePreferredLanguage_fallbackLocaleMustNotBeNull() {
        Locale testLocale = Locale.ROOT;
        Set<Locale> supportedLocales = Collections.singleton(testLocale);
        List<LanguageRange> acceptedLanguages = Arrays.asList(new LanguageRange(testLocale.toLanguageTag()));
        
        I18nUtil.resolvePreferredLanguage(testLocale, null, acceptedLanguages, supportedLocales);
    }

    @Test(expected = NullPointerException.class)
    public void test_resolvePreferredLanguage_acceptedLanguagesMustNotBeNull() {
        Locale testLocale = Locale.ROOT;
        Set<Locale> supportedLocales = Collections.singleton(testLocale);
        
        I18nUtil.resolvePreferredLanguage(testLocale, testLocale, null, supportedLocales);
    }

    @Test(expected = NullPointerException.class)
    public void test_resolvePreferredLanguage_supportedLocalesMustNotBeNull() {
        Locale testLocale = Locale.ROOT;
        List<LanguageRange> acceptedLanguages = Arrays.asList(new LanguageRange(testLocale.toLanguageTag()));
        
        I18nUtil.resolvePreferredLanguage(testLocale, testLocale, acceptedLanguages, null);
    }

    /**
     * When:
     *  - A preferred language is provided
     *  - There exists an accepted language with the same weight
     *  - Both languages are supported
     * Then:
     *  - The preferred language should be the selected language
     */
    @Test
    public void test_resolvePreferredLanguage_preferredLanguageIsHighestPriorityWhenEqual() {
        Locale preferredLocale = Locale.ENGLISH;
        Locale testLocale = Locale.ROOT;

        Set<Locale> supportedLocales = new HashSet<Locale>(Arrays.asList(preferredLocale, testLocale));
        LanguageRange acceptedRange = new LanguageRange(testLocale.toLanguageTag(), LanguageRange.MAX_WEIGHT);
        List<LanguageRange> acceptedLanguages = Arrays.asList(acceptedRange);
        
        Locale locale = I18nUtil.resolvePreferredLanguage(preferredLocale, testLocale, acceptedLanguages, supportedLocales);
        Assert.assertEquals(preferredLocale, locale);
    }

    /**
     * When:
     *  - A preferred language might be provided
     *  - The set of accepted languages does not intersect the set of supported locales
     * Then:
     *  - The fallback language should be the returned language
     */
    @Test
    public void test_resolvePreferredLanguage_fallbackLanguageIsChosenIfNothingIsSupported() {
        Locale preferredLocale = Locale.ENGLISH;
        Locale testLocale = Locale.ROOT;

        Set<Locale> supportedLocales = new HashSet<Locale>();
        List<LanguageRange> acceptedLanguages = new ArrayList<LanguageRange>();
        
        Locale locale = I18nUtil.resolvePreferredLanguage(preferredLocale, testLocale, acceptedLanguages, supportedLocales);
        Assert.assertEquals(testLocale, locale);
    }
}
