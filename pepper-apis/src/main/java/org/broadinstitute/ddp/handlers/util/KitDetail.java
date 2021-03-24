package org.broadinstitute.ddp.handlers.util;

import lombok.NonNull;
import org.broadinstitute.ddp.datstat.ParticipantFields;

import java.util.Comparator;

public class KitDetail {


    public KitDetail() {

    }

    public enum KitDetailField {

        participantId(ParticipantFields.ID.getDSMValue()),
        kitRequestId("kitRequestId"),
        kitType("kitType");


        private String fieldName;

        KitDetailField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return this.fieldName;
        }

    }

    private transient int reqId; //real requestId (not hashed) to be used for sorting in @KitDetailComp
    private String participantId; //UUID
    private String kitRequestId; //UUID
    private String kitType;

    public KitDetail(String participantId, String kitRequestId, String kitType, int reqId) {
        this.participantId = participantId;
        this.kitRequestId = kitRequestId;
        this.kitType = kitType;
        this.reqId = reqId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getKitRequestId() {
        return kitRequestId;
    }

    public void setKitRequestId(String kitRequestId) {
        this.kitRequestId = kitRequestId;
    }

    public String getKitType() {
        return kitType;
    }

    public void setKitType(String kitType) {
        this.kitType = kitType;
    }

    public int getReqId() {
        return reqId;
    }

    public void setReqId(int reqId) {
        this.reqId = reqId;
    }

    static public class KitDetailComp implements Comparator<KitDetail>
    {
        @Override
        public int compare(@NonNull KitDetail k1, @NonNull KitDetail k2) {
            if (k1.getReqId() > k2.getReqId()) {
                return 1;
            } else if (k1.getReqId() < k2.getReqId()) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}


