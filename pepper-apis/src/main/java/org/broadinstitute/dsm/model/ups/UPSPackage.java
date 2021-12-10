package org.broadinstitute.dsm.model.ups;

import lombok.Data;

@Data
public class UPSPackage {
    String trackingNumber;
    UPSActivity[] activity;
    String upsShipmentId;
    UPSDeliveryDate[] deliveryDate;
    UPSDeliveryTime deliveryTime;
    String upsPackageId;


    public UPSPackage(String trackingNumber, UPSActivity[] activity, String upsShipmentId, String upsPackageId,
                      UPSDeliveryDate[] deliveryDate, UPSDeliveryTime deliveryTime) {
        this.trackingNumber = trackingNumber;
        this.activity = activity;
        this.upsShipmentId = upsShipmentId;
        this.upsPackageId = upsPackageId;
        this.deliveryDate = deliveryDate;
        this.deliveryTime = deliveryTime;
    }


    /**
     * Gets the earliest {@link UPSActivity} that indicates
     * actual package motion, such as pickup, transit or delivery.
     * Excludes prep steps like "We see there's a package
     * to be created" and exceptions.
     */
    public UPSActivity getEarliestPackageMovementEvent() {
        return getEarliestFilterredEvent(UPSActivity::isOnItsWay);
    }

    public UPSActivity getEarliestFilterredEvent(EventFilter filter) {
        UPSActivity earliestFilteredEvent = null;
        for (UPSActivity event : activity) {
            if (filter.includeEvent(event)) {
                if (earliestFilteredEvent == null) {
                    earliestFilteredEvent = event;
                }
                else if (event!=null && event.getInstant()!= null && event.getInstant().isBefore(earliestFilteredEvent.getInstant())) {
                    earliestFilteredEvent = event;
                }
            }
        }
        return earliestFilteredEvent;
    }

    public String printActivity() {
        StringBuilder stringBuilder = new StringBuilder();
        for (UPSActivity activity : activity) {
            UPSStatus status = activity.getStatus();
            stringBuilder.append(activity.getInstant() + " " + status.getType() + " " + status.getCode() + " " + status.getDescription());
            stringBuilder.append("\n");
        }
        return stringBuilder.toString();
    }

    @FunctionalInterface
    private interface EventFilter {
        boolean includeEvent(UPSActivity event);
    }
}

