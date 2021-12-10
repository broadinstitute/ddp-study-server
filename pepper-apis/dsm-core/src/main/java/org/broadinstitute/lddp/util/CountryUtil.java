package org.broadinstitute.lddp.util;

import lombok.NonNull;
import org.broadinstitute.lddp.util.CountryCode;

import java.util.*;

public class CountryUtil {

    /**
     * Returns a list of countries ordered by the name
     * of the country, with US at the top of the list
     * @return
     */
    public List<CountryCode> getUSFirstNameOrderedCountries() {
        List<CountryCode> usFirstList = new ArrayList<>();
        Set<CountryCode> orderedCountries = new TreeSet<>(new CountryCodeComparator());
        CountryCode us = null;
        String[] locales = Locale.getISOCountries();
        for (String countryCode : locales) {
            Locale obj = new Locale("", countryCode);
            CountryCode cc = new CountryCode(obj.getCountry(), obj.getDisplayCountry());
            if ("US".equals(obj.getCountry())) {
                us = cc;
            }
            else {
                orderedCountries.add(cc);
            }
        }
        usFirstList.addAll(orderedCountries);
        usFirstList.add(0,us);
        return usFirstList;
    }

    private static class CountryCodeComparator implements Comparator<CountryCode> {

        @Override
        public int compare(@NonNull CountryCode o1, @NonNull CountryCode o2) {
            return o1.getCountryName().compareTo(o2.getCountryName());
        }
    }
}