package org.broadinstitute.ddp.model.address;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Model for countries used in mailing addresses. Note that from the perspective of mail addresses
 * a territory, such as Puerto Rico, could be a country
 */
public class CountryAddressInfo {
    /**
     * DB id.
     */
    private transient long id;
    /**
     * Two-letter postal code.
     */
    private String code;
    /**
     * Country name.
     */
    private String name;
    /**
     * "State" or "Province" are most common here.
     */
    private String subnationalDivisionTypeName;
    /**
     * If we have them, here are the subnational divisions (usually states or provinces).
     */
    private List<SubnationalDivision> subnationalDivisions = new ArrayList<>();
    /**
     * "ZIP" or "Postal Code' or something else.
     */
    private String postalCodeLabel;
    /**
     * Sometimes there is a postal code we can use for validations.
     */
    private String postalCodeRegex;
    /**
     * If special case where this instance is really a territory, we might need to know the the code to use
     * for state.
     * e.g., for Puarto Rico the country code is "PR" and the state code is also "PR"
     */
    private String stateCode;


    public CountryAddressInfo() {
        super();
    }

    @ColumnName("country_address_info_id")
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @ColumnName("subnational_division_type_label")
    public String getSubnationalDivisionTypeName() {
        return subnationalDivisionTypeName;
    }


    public void setPostalCodeLabel(String postalCodeLabel) {
        this.postalCodeLabel = postalCodeLabel;
    }

    public void setPostalCodeRegex(String postalCodeRegex) {
        this.postalCodeRegex = postalCodeRegex;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public void setSubnationalDivisionTypeName(String subnationalDivisionTypeName) {
        this.subnationalDivisionTypeName = subnationalDivisionTypeName;
    }

    public List<SubnationalDivision> getSubnationalDivisions() {
        return subnationalDivisions;
    }

    public Optional<SubnationalDivision> getSubnationDisivisionByCode(String divisionCode) {
        return this.subnationalDivisions.stream().filter(s -> s.getCode().equals(divisionCode)).findFirst();
    }

    public void addSubnationalDivision(SubnationalDivision division) {
        this.getSubnationalDivisions().add(division);
    }

    public String getPostalCodeLabel() {
        return postalCodeLabel;
    }

    public String getPostalCodeRegex() {
        return postalCodeRegex;
    }

    public String getStateCode() {
        return stateCode;
    }


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


}
