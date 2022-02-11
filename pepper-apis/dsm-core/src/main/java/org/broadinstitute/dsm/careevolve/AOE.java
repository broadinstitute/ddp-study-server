package org.broadinstitute.dsm.careevolve;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Ask at Order Entry tuple, which has a code,
 * description, and value
 */
public class AOE {

    @SerializedName("AOECode")
    private String code;

    @SerializedName("AOEDescription")
    private String description;

    @SerializedName("AOEAnswer")
    private String value;

    public AOE(String code, String description, String value) {
        this.code = code;
        this.description = description;
        this.value = value;
    }

    /**
     * Creates a list of AOES used for TestBoston
     */
    public static List<AOE> forTestBoston(String userGuid, String kitGuid) {
        List<AOE> aoes = new ArrayList<>();
        aoes.add(new AOE("Q1","Type of Swab","AN SWAB"));
        aoes.add(new AOE("PEPPER_USER_ID","Pepper User Guid",userGuid));
        aoes.add(new AOE("PEPPER_KIT_ID","Pepper Kit Guid",kitGuid));
        return aoes;
    }
}
