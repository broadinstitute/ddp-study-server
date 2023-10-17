package org.broadinstitute.ddp.db.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The name of a cancer and its language
 */
public class CancerItem {

    @SerializedName("name")
    private final String cancerName;

    @SerializedName("language")
    private final String language;

    public CancerItem(String cancerName, String language) {
        this.cancerName = cancerName;
        this.language = language;
    }

    public String getCancerName() {
        return cancerName;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CancerItem that = (CancerItem) o;
        return cancerName.equals(that.cancerName) && language.equals(that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cancerName, language);
    }

    /**
     * Creates a list of cancer items for the given language
     */
    public static List<CancerItem> toCancerItemList(List<String> cancerNames, String languageIsoCode) {
        List<CancerItem> cancers = new ArrayList<>();
        for (String cancerName : cancerNames) {
            cancers.add(new CancerItem(cancerName, languageIsoCode));
        }
        return cancers;
    }
}
