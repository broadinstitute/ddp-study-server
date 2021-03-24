package org.broadinstitute.dsm.careevolve;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class Order {

    @SerializedName("AOEs")
    private List<AOE> aoes = new ArrayList<>();

    @SerializedName("Collection")
    private String collectionTime;

    @SerializedName("Patient")
    private Patient patient;

    @SerializedName("Provider")
    private Provider provider;

    /**
     * This is the label on the tube that will
     * be scanned into Mercury during accessioning
     */
    @SerializedName("OrderId")
    private String kitLabel;

    @SerializedName("CareEvolveAccount")
    private String account;


    private static final String TEST_CODE = "Covid19_Diagnostic";

    @SerializedName("TestCode")
    private String testCode = TEST_CODE;

    @SerializedName("TestDescription")
    private final String testDescription = TEST_CODE;

    public Order(String account, Patient patient, String kitLabel, Instant collectionTime, Provider provider, List<AOE> aoes) {
        this.account = account;
        this.provider = provider;
        this.aoes = aoes;
        this.patient = patient;
        this.kitLabel = kitLabel;
        // we rely on ISO8601
        this.collectionTime = collectionTime.toString();
    }

    public String getOrderId() {
        return kitLabel;
    }
}
