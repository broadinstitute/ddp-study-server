package org.broadinstitute.ddp.model.kit;

import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PendingScheduleRecord {

    private long studyId;
    private String studyGuid;
    private String userGuid;
    private Long addressId;
    private DsmAddressValidationStatus addressValidationStatus;
    private KitScheduleRecord record;

    @JdbiConstructor
    public PendingScheduleRecord(
            @ColumnName("study_id") long studyId,
            @ColumnName("study_guid") String studyGuid,
            @ColumnName("user_guid") String userGuid,
            @ColumnName("address_id") Long addressId,
            @ColumnName("address_validation_status") DsmAddressValidationStatus addressValidationStatus,
            @Nested KitScheduleRecord record) {
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.userGuid = userGuid;
        this.addressId = addressId;
        this.addressValidationStatus = addressValidationStatus;
        this.record = record;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public long getUserId() {
        return record.getUserId();
    }

    public String getUserGuid() {
        return userGuid;
    }

    public Long getAddressId() {
        return addressId;
    }

    public DsmAddressValidationStatus getAddressValidationStatus() {
        return addressValidationStatus;
    }

    public KitScheduleRecord getRecord() {
        return record;
    }
}
