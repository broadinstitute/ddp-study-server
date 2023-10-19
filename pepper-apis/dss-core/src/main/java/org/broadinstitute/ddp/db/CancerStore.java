package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.json.CancerSuggestionResponse;
import org.broadinstitute.ddp.model.dsm.Cancer;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;
import org.broadinstitute.ddp.model.suggestion.PatternMatch;
import org.broadinstitute.ddp.util.StringSuggestionTypeaheadComparator;

public class CancerStore {

    private static List<CancerItem> cancers;
    private static CancerStore instance;
    private static volatile Object lockVar = "lock";
    private final Map<String, Set<CancerItem>> cancersByLanguage = new HashMap<>();

    private CancerStore() {
        cancers = new ArrayList<>();
    }

    public static CancerStore getInstance() {
        if (instance == null) {
            synchronized (lockVar) {
                if (instance == null) {
                    instance = new CancerStore();
                }
            }
        }
        return instance;
    }

    public synchronized void populate(List<CancerItem> cancerItems) {
        if (cancerItems != null) {
            cancers = Collections.unmodifiableList(cancerItems);
            cancersByLanguage.clear();
            for (CancerItem cancer : cancers) {
                if (!cancersByLanguage.containsKey(cancer.getIsoLanguageCode())) {
                    cancersByLanguage.put(cancer.getIsoLanguageCode(), new LinkedHashSet<>());
                }
                cancersByLanguage.get(cancer.getIsoLanguageCode()).add(cancer);
            }
        }
    }

    /**
     * Returns all the cancers for the given language
     */
    public Set<CancerItem> getCancerList(String languageIsoCode) {
        if (cancersByLanguage.get(languageIsoCode) == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(cancersByLanguage.get(languageIsoCode));
        }
    }

    /**
     * Returns all the cancers.  Special case for a blank query.
     */
    public CancerSuggestionResponse getAllCancers(String language, int limit) {
        List<CancerSuggestion> cancerSuggestions = getInstance().getCancerList(language).stream().map(
                cancer -> new CancerSuggestion(
                        new Cancer(cancer.getCancerName(), null),
                        List.of()
                )
        ).limit(limit).collect(Collectors.toList());
        return new CancerSuggestionResponse("", cancerSuggestions);
    }

    /**
     * Returns a list of at most limit size that contains cancers that match the
     * given text for the given language, sorted in order so that "most useful" matches
     * appear earlier in the list than "less useful" matches.
     */
    public CancerSuggestionResponse getCancerSuggestions(String cancerQuery, String language, int limit) {
        String upperCancerQuery = cancerQuery.toUpperCase();
        boolean isSpanish = LanguageStore.SPANISH_LANG_CODE.equalsIgnoreCase(language);
        String asciiCharsReplacedWithSpanishChars = upperCancerQuery;

        // first pass filter: find simple matches
        Set<CancerItem> cancerMatches = new HashSet<>(getInstance().getCancerList(language).stream()
                .filter(cancer -> cancer.getCancerName().toUpperCase().contains(upperCancerQuery))
                .collect(Collectors.toList()));

        if (isSpanish) {
            // for Spanish, replace unaccented chars with accented chars so that we find matches
            // with different keyboard configurations.
            String[] accentedReplacements = new String[] {"ü", "ñ", "é", "á", "í", "ó", "ú"};
            String[] unaccentedInputs =    new String[] {"u", "n", "e", "a", "i", "o", "u"};

            for (int charIndex = 0; charIndex < accentedReplacements.length; charIndex++) {
                // look for a match by replacing one character at a time
                String charToReplace = unaccentedInputs[charIndex].toUpperCase();
                String replacementChar = accentedReplacements[charIndex].toUpperCase();
                asciiCharsReplacedWithSpanishChars = asciiCharsReplacedWithSpanishChars.replaceAll(charToReplace,
                        "(" + charToReplace + "|" + replacementChar + ")");
            }

            String regex = asciiCharsReplacedWithSpanishChars;
            cancerMatches.addAll(getInstance().getCancerList(language).stream()
                    .filter(cancer -> Pattern.compile(regex).matcher(
                            cancer.getCancerName().toUpperCase()).find())
                    .collect(Collectors.toList()));
        }

        // now rank the matches in a way that puts left-most matches near the top, favoring word start matches
        List<CancerSuggestion> sortedSuggestions = new ArrayList<>();
        String regex = upperCancerQuery;
        if (isSpanish) {
            // search for the exact match or the match with Spanish accented chars substituted
            regex = String.format("((%s)|(%s))", regex, asciiCharsReplacedWithSpanishChars);
        }
        Pattern pattern = Pattern.compile(regex);
        var suggestionComparator = new StringSuggestionTypeaheadComparator(upperCancerQuery);
        cancerMatches.stream()
                .sorted((lhs, rhs) -> suggestionComparator.compare(lhs.getCancerName(), rhs.getCancerName()))
                .limit(limit)
                .forEach(cancer -> {
                    Matcher matcher = pattern.matcher(cancer.getCancerName().toUpperCase());
                    if (matcher.find()) {
                        int offset = matcher.start();
                        int hitLength = matcher.end() - offset;
                        sortedSuggestions.add(new CancerSuggestion(new Cancer(cancer.getCancerName()),
                                Collections.singletonList(new PatternMatch(offset, hitLength))));
                    }
                });

        return new CancerSuggestionResponse(cancerQuery, sortedSuggestions);
    }

}
