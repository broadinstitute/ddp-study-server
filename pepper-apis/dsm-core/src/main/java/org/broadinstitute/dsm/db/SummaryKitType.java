package org.broadinstitute.dsm.db;

import lombok.Data;

@Data
public class SummaryKitType {

    public String kitType;
    public int newK;
    public int sent;
    public int received;
    public int newPeriod;
    public int sentPeriod;
    public int receivedPeriod;
    public String month;

    public SummaryKitType(String kitType, int newK, int sent, int received, int newPeriod, int sentPeriod, int receivedPeriod) {
        this.kitType = kitType;
        this.newK = newK;
        this.sent = sent;
        this.received = received;
        this.newPeriod = newPeriod;
        this.sentPeriod = sentPeriod;
        this.receivedPeriod = receivedPeriod;
    }

    public SummaryKitType(String kitType, int sent, int received, String month) {
        this.kitType = kitType;
        this.sent = sent;
        this.received = received;
        this.month = month;
    }
}
