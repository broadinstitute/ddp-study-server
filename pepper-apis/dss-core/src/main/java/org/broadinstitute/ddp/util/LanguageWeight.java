package org.broadinstitute.ddp.util;

/**
 * Represent a language and its weight from a request header.
 */
public class LanguageWeight {

    private String code;
    private String weight;

    public LanguageWeight() {}

    public LanguageWeight(String code, String weight) {
        this.code = code;
        this.weight = weight;
    }

    public String getCode() {
        return code;
    }

    public LanguageWeight setCode(String code) {
        this.code = code;
        return this;
    }

    public String getWeight() {
        return weight;
    }

    public LanguageWeight setWeight(String weight) {
        this.weight = weight;
        return this;
    }
}
