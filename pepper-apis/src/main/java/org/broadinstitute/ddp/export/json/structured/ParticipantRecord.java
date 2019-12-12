package org.broadinstitute.ddp.export.json.structured;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

// A representation of a participant, a part of the model used in the
// "participants_structured" index in Elasticsearch
public class ParticipantRecord {

    @SerializedName("status")
    private EnrollmentStatusType status;
    @SerializedName("statusTimestamp")
    private long statusTimestamp;
    @SerializedName("profile")
    private ParticipantProfile profile;
    @SerializedName("address")
    private AddressRecord address;
    @SerializedName("medicalProviders")
    private List<ProviderRecord> medicalProviders;
    @SerializedName("activities")
    private List<ActivityInstanceRecord> activityInstanceRecords;
    @SerializedName("dsm")
    private DsmComputedRecord dsmComputedRecord;

    public ParticipantRecord(
            EnrollmentStatusType enrollmentStatus,
            long enrollmentStatusTimestamp,
            ParticipantProfile profile,
            List<ActivityInstanceRecord> activityInstanceRecords,
            List<MedicalProviderDto> medicalProviders,
            MailAddress mailAddress,
            DsmComputedRecord dsmComputedRecord
    ) {
        this.status = enrollmentStatus;
        this.statusTimestamp = enrollmentStatusTimestamp;
        this.profile = profile;
        this.activityInstanceRecords = activityInstanceRecords;

        this.medicalProviders = medicalProviders.stream()
                .map(ProviderRecord::new)
                .collect(Collectors.toList());

        if (mailAddress != null) {
            this.address = new AddressRecord(mailAddress);
        }

        this.dsmComputedRecord = dsmComputedRecord;
    }
}
