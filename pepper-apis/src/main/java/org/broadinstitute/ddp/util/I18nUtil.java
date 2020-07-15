package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.json.activity.TranslatedSummary;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I18nUtil {
    // Might want to pull this from a config file at some point, although
    // there is also Locale.getDefault() (bskinner)
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private static final Logger LOG = LoggerFactory.getLogger(I18nUtil.class);

    /**
     * Given a collection of summaries, groups them by GUID and language code
     * Additionally, figures out the secondary language
     *
     * @param activitySummaries     A collection to scan
     * @param preferredLanguageCode User's preferred language code used for figuring out the secondary language
     * @return Summaries grouped by GUID and language
     */

    private static <T extends TranslatedSummary> ActivitySummariesGroup<T> groupSummariesByGuidAndLanguage(
            Collection<T> activitySummaries,
            String preferredLanguageCode
    ) {
        ActivitySummariesGroup activitySummariesByGuid = new ActivitySummariesGroup();
        activitySummaries.forEach(
                as -> {
                    String guid = as.getActivityInstanceGuid();
                    String lang = as.getIsoLanguageCode();
                    activitySummariesByGuid.addSummary(guid, lang, as);
                    // The first found language that is neither English nor the preferred user's language becomes
                    // the "secondary" language. In addition, it gets "sticky". We fall back to it later unless
                    // a translation to the preferred language exists
                    if (!lang.equals(LanguageConstants.EN_LANGUAGE_CODE) && !lang.equals(preferredLanguageCode)) {
                        activitySummariesByGuid.registerSecondaryLanguage(lang);
                    }
                }
        );
        return activitySummariesByGuid;
    }

    /**
     * Given the collection of summaries' mappings (where same summaries are grouped by language code),
     * returns summaries translated to the preferred language (or falls back to the secondary one)
     *
     * @param activitySummariesByLang Summaries groupped by language
     * @param preferredLanguageCode   Preferred language code for user
     * @return A list containing the single translated summary
     */
    public static <T extends TranslatedSummary> List<T> getActivityInstanceTranslation(
            Collection<T> activitySummaries,
            String preferredLanguageCode
    ) {
        ActivitySummariesGroup<T> activitySummariesByLang = groupSummariesByGuidAndLanguage(
                activitySummaries, preferredLanguageCode
        );
        String secondaryLanguage = activitySummariesByLang.secondaryLanguage();
        List<Object> summariesTranslatedToPreferredLang = new ArrayList<>();
        activitySummariesByLang.content().values().forEach(
                summaryByLang -> {
                    List<T> candidateSummaries = Arrays.asList(
                            summaryByLang.get(preferredLanguageCode),              // The highest-prio lang is the one preferred by user
                            summaryByLang.get(LanguageConstants.EN_LANGUAGE_CODE), // If it's not found, try English
                            summaryByLang.get(secondaryLanguage)                   // Finally, trying to fall back to what else is available
                    );
                    T chosenSummary = (T) ObjectUtils.firstNonNull(candidateSummaries.toArray());
                    if (chosenSummary != null) {
                        summariesTranslatedToPreferredLang.add(chosenSummary);
                    }
                }
        );
        return (List) summariesTranslatedToPreferredLang;
    }

    /**
     * Select best available locale option based on given parameters. If study is provided, it will be used to lookup
     * supported languages. If header is provided, it will be used to set priority of languages. If preferred locale is
     * provided, it will come after the accepted languages. If nothing is provided, will fallback to the default.
     *
     * @param handle               the database handle
     * @param studyGuid            the study guid, optional
     * @param preferredLocale      the preferred locale, optional
     * @param acceptLanguageHeader the request header, optional
     * @return the resolved locale
     */
    public static Locale resolveLocale(Handle handle, String studyGuid, Locale preferredLocale, String acceptLanguageHeader) {
        List<Locale.LanguageRange> acceptedRanges = Collections.emptyList();
        if (StringUtils.isNotEmpty(acceptLanguageHeader)) {
            try {
                acceptedRanges = Locale.LanguageRange.parse(acceptLanguageHeader);
            } catch (Exception e) {
                LOG.warn("Error while parsing Accept-Language header '{}', will disregard and continue", acceptLanguageHeader, e);
            }
        }

        Locale studyDefault = null;
        Set<Locale> studyLocales = new HashSet<>();
        if (studyGuid != null) {
            List<StudyLanguage> studyLanguages = handle.attach(StudyLanguageDao.class).findLanguages(studyGuid);
            //@todo fix back when done
            // List<StudyLanguage> studyLanguages = new StudyLanguageCachedDao(handle).findLanguages(studyGuid);
            for (StudyLanguage language : studyLanguages) {
                Locale locale = language.toLocale();
                studyLocales.add(locale);
                if (language.isDefault()) {
                    studyDefault = locale;
                }
            }
        }

        if (studyDefault == null) {
            studyDefault = I18nUtil.DEFAULT_LOCALE;
            if (studyGuid != null) {
                LOG.warn("Study {} does not have a default language, will fallback to {}", studyGuid, studyDefault.getLanguage());
            }
        }

        return resolvePreferredLanguage(preferredLocale, studyDefault, acceptedRanges, studyLocales);
    }

    /**
     * Select the best available Locale option given a set of weights and constraints.
     *
     * <p>This method behaves the same as {@link I18nUtil#resolvePreferredLanguage(Locale, Locale, List, Set)},
     * but uses {@link I18nUtil#DEFAULT_LOCALE} as the fallback language.
     *
     * <p>This method is guaranteed to always return a valid locale. If there is no
     * intersection between the preferred locale and accepted languages with the
     * supported locales- or no supported locales are provided- a default locale will be returned.
     *
     * @param preferredLocale  the ideal locale to be selected
     * @param acceptLanguages  a prioritized list of weighted alternative locale options
     * @param supportedLocales a set of the available locales to prioritize against
     * @return the best available locale given the constraints
     * @see #DEFAULT_LOCALE
     */
    @Nonnull
    public static Locale resolvePreferredLanguage(@Nullable Locale preferredLocale,
                                                  @Nonnull List<LanguageRange> acceptLanguages, @Nonnull Set<Locale> supportedLocales) {
        return resolvePreferredLanguage(preferredLocale, I18nUtil.DEFAULT_LOCALE, acceptLanguages, supportedLocales);
    }

    /**
     * Select the best available Locale option given a set of weights and constraints.
     *
     * <p>A valid locale is guaranteed to be returned. If there is no
     * intersection between the preferred locale and accepted languages with the
     * supported locales- or no supported locales are provided- the fallback locale will be returned.
     *
     * @param preferredLocale  the ideal locale to return, if present in the supported locales set
     * @param fallbackLocale   a fallback locale to return if the preferred and accepted locales are insufficient
     * @param acceptLanguages  a prioritized list of weighted alternative locale options
     * @param supportedLocales a set of the available locales to prioritize against
     * @return the best available locale given the constraints
     */
    @Nonnull
    public static Locale resolvePreferredLanguage(@Nullable Locale preferredLocale, @Nonnull Locale fallbackLocale,
                                                  @Nonnull List<LanguageRange> acceptLanguages, @Nonnull Set<Locale> supportedLocales) {
        MiscUtil.checkNonNull(fallbackLocale, "fallbackLocale");
        MiscUtil.checkNonNull(acceptLanguages, "acceptLanguages");
        MiscUtil.checkNonNull(supportedLocales, "supportedLocales");

        List<LanguageRange> priorityList = new ArrayList<LanguageRange>(acceptLanguages);

        if (null != preferredLocale) {
            LanguageRange preferred = new LanguageRange(preferredLocale.toLanguageTag(), LanguageRange.MAX_WEIGHT);

            // This is the locale the user has specifically chosen, insert it
            // at the bottom of the list so it has the lowest priority
            priorityList.add(preferred);
        }

        LanguageRange fallback = new LanguageRange(fallbackLocale.toLanguageTag(), LanguageRange.MIN_WEIGHT);
        priorityList.add(fallback);

        List<Locale> supportedLocalesWithFallback = new ArrayList<Locale>(supportedLocales);
        supportedLocalesWithFallback.add(fallbackLocale);

        Locale found = Locale.lookup(priorityList, supportedLocalesWithFallback);
        return found != null ? found : fallbackLocale;
    }

    /**
     * Serves as a container for the following structure: { "ABBA": { "en": { id: 1 } } }
     * inst guid ^     ^ lang    ^ activity instance
     */
    public static class ActivitySummariesGroup<T> {
        private Map<String, Map<String, T>> summariesByGuidAndLang = new LinkedHashMap<>();
        public String secondaryLanguage;

        public void addSummary(String guid, String isoLanguageCode, T summary) {
            Map<String, T> summariesByLang = summariesByGuidAndLang.get(guid);
            if (summariesByLang == null) {
                summariesByLang = new HashMap<>();
                summariesByGuidAndLang.put(guid, summariesByLang);
            }
            summariesByLang.put(isoLanguageCode, summary);
        }

        public Map<String, Map<String, T>> content() {
            for (Map<String, T> summariesByLang : summariesByGuidAndLang.values()) {
                summariesByLang = Collections.unmodifiableMap(summariesByLang);
            }
            return Collections.unmodifiableMap(summariesByGuidAndLang);
        }

        public String secondaryLanguage() {
            return secondaryLanguage;
        }

        public void registerSecondaryLanguage(String secondaryLanguage) {
            this.secondaryLanguage = secondaryLanguage;
        }
    }
}
