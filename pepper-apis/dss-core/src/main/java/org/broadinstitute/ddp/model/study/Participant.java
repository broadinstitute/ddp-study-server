package org.broadinstitute.ddp.model.study;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.export.json.structured.FileRecord;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.user.User;

/**
 * Represents a user who is participating in a study. Includes system-wide user data, as well as study-specific data like medical providers
 * and activity responses.
 */
public class Participant {

    private User user;
    private Map<String, List<ActivityResponse>> responses;
    private Map<Long, Map<String, String>> activityInstanceSubstitutions;

    /**
     * Map with addresses: key = instanceId, value = MailAddress (which guid stored in activity_instance_substitution ADDRESS_GUID
     */
    private Map<Long, MailAddress> nonDefaultMailAddresses;

    // todo: better models for status and medical providers
    private EnrollmentStatusDto status;
    private List<MedicalProviderDto> providers;
    private LocalDate dateOfMajority;
    private LocalDate birthDate;
    private DateValue dateOfDiagnosis;
    private List<InvitationDto> invitations;
    private List<FileRecord> files;

    public Participant(EnrollmentStatusDto status, User user) {
        this.status = status;
        this.user = user;
        this.providers = new ArrayList<>();
        this.invitations = new ArrayList<>();
        this.files = new ArrayList<>();
        this.responses = new HashMap<>();
        this.activityInstanceSubstitutions = new HashMap<>();
        this.nonDefaultMailAddresses = new HashMap<>();
    }

    public EnrollmentStatusDto getStatus() {
        return status;
    }

    public User getUser() {
        return user;
    }

    public List<MedicalProviderDto> getProviders() {
        return providers;
    }

    public void addProvider(MedicalProviderDto providerDto) {
        providers.add(providerDto);
    }


    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public DateValue getDateOfDiagnosis() {
        return dateOfDiagnosis;
    }

    public void setDateOfDiagnosis(DateValue dateOfDiagnosis) {
        this.dateOfDiagnosis = dateOfDiagnosis;
    }

    public List<ActivityResponse> getAllResponses() {
        return responses.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public void clearAllResponses() {
        responses.clear();
    }

    public List<ActivityResponse> getResponses(String activityTag) {
        return responses.computeIfAbsent(activityTag, tag -> new ArrayList<>());
    }

    public void addResponse(ActivityResponse response) {
        responses.computeIfAbsent(response.getActivityTag(), tag -> new ArrayList<>()).add(response);
    }

    public Map<String, String> getActivityInstanceSubstitutions(long activityInstanceId) {
        return activityInstanceSubstitutions.getOrDefault(activityInstanceId, new HashMap<>());
    }

    public void putActivityInstanceSubstitutions(long activityInstanceId, Map<String, String> substitutions) {
        activityInstanceSubstitutions.put(activityInstanceId, substitutions);
    }

    public Map<Long, MailAddress> getNonDefaultMailAddresses() {
        return nonDefaultMailAddresses;
    }

    public void associateParticipantInstancesWithNonDefaultAddresses(List<MailAddress> participantNonDefaultAddresses) {
        nonDefaultMailAddresses = new HashMap<>();
        for (Map.Entry<Long, Map<String, String>> instanceEntry : activityInstanceSubstitutions.entrySet()) {
            for (Map.Entry<String, String> subsEntry : instanceEntry.getValue().entrySet()) {
                if (subsEntry.getKey().equals(I18nTemplateConstants.Snapshot.ADDRESS_GUID)) {
                    MailAddress mailAddress = participantNonDefaultAddresses
                            .stream()
                            .filter(m -> m.getGuid().equals(subsEntry.getValue()))
                            .findFirst()
                            .orElseThrow(() -> new DaoException(
                                    "Inconsistent non-default address data in DB (address stored in instance subscriptions "
                                            + "is not found in participants address). AddressGuid=" + subsEntry.getValue()));
                    nonDefaultMailAddresses.put(instanceEntry.getKey(), mailAddress);
                }
            }
        }
    }

    public LocalDate getDateOfMajority() {
        return dateOfMajority;
    }

    public void setDateOfMajority(LocalDate dateOfMajority) {
        this.dateOfMajority = dateOfMajority;
    }

    public List<InvitationDto> getInvitations() {
        return invitations;
    }

    public void addInvitation(InvitationDto invitationDto) {
        this.invitations.add(invitationDto);
    }

    public List<FileRecord> getFiles() {
        return files;
    }

    public void addAllFiles(List<FileRecord> files) {
        if (files != null) {
            this.files.addAll(files);
        }
    }
}
