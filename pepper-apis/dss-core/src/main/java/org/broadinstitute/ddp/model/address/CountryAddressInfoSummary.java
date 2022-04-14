package org.broadinstitute.ddp.model.address;

/**
 * Summary object for country info used for mailing purposes. Only care about name and code
 */
public class CountryAddressInfoSummary {
    //transient to exclude from Gson
    private transient long id;
    private String name;
    private String code;

    public CountryAddressInfoSummary() {
        super();
    }

    public CountryAddressInfoSummary(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
