package org.broadinstitute.dsm.route.kit;

public interface ScanPayload {

    String getKitLabel();

    void setKitLabel(String kitLabel);

    String getTrackingReturnId();

    String getDdpLabel();

    String getHruid();

    void setHruid(String hruid);

    String getRNA();

}
