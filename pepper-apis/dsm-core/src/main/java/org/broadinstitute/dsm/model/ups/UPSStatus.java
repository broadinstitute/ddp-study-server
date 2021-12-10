package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class UPSStatus {
    String type;
    String description;
    String code;

    public static final String IN_TRANSIT_TYPE = "I";

    public static final String DELIVERED_TYPE = "D";

    public static final String PICKUP_TYPE = "P";

    public static final String OUT_FOR_DELIVERY_TYPE = "O";


    public UPSStatus(String type, String description, String code) {
        this.type = type;
        this.description = description;
        this.code = code;
    }
    /**
     * Returns whether this status indicates physical
     * movement of the package according to heuristics
     * based on shipping history.
     */
    public boolean isOnItsWay() {
        return IN_TRANSIT_TYPE.equals(type) ||
                OUT_FOR_DELIVERY_TYPE.equals(type) ||
                isDelivery() ||
                isPickup();
    }

    public boolean isDelivery() {
        return DELIVERED_TYPE.equals(type);
    }

    public boolean isPickup() {
        return PICKUP_TYPE.equals(type);
    }
}
