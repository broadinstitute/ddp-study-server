package org.broadinstitute.ddp.model.user;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;

public enum EnrollmentStatusType {

    /**
     * Means participant is registered with study but not yet enrolled. Typically this is after they have created an
     * account but before giving consent.
     */
    REGISTERED(true, false, false, true),
    /**
     * Means participant is fully enrolled in study. Typically this is after they have completed consent.
     */
    ENROLLED(true, false, false,  true),
    /**
     * Means participant withdrew from study before being enrolled.
     */
    EXITED_BEFORE_ENROLLMENT(false, true, true, false),
    /**
     * Means participant withdrew from study after being enrolled.
     */
    EXITED_AFTER_ENROLLMENT(false, true, true, false),
    /**
     * Means the consent participant given is no longer valid, and thus suspended. Participant is no longer considered
     * enrolled until they re-consent. Typically this can happen when a minor participant ages up. Their parental
     * consent will be suspended and they will need to give their own consent.
     */
    CONSENT_SUSPENDED(true, false, false, true),
    /**
     * Means participant has reached the end of participation in the study. This is very similar to `ENROLLED`. They are
     * considered to be fully enrolled in the study and can continue to view/edit their data, but will not be
     * participating in future activities (e.g. will not receive any more kits).
     */
    COMPLETED(true, false, false, true);

    private final boolean shouldReceiveCommunications;
    private final boolean isExited;
    private final boolean shouldMarkActivitiesAsReadOnly;
    private final boolean isDSMVisible;

    /**
     * Individual statuses have a few different behaviors, so rather than requiring
     * client code to do their own grouping/filtering, we declare the state behaviors
     * as enum params
     * @param shouldReceiveCommunications If true, participants in this state
     *                                    should receive communications such as reminder
     *                                    emails.  If false, no communications should be sent,
     *                                    even if they are queued.
     * @param isExited If true, the state is interpreted as formally exiting a study,
     *                 for any number of reasons.  If false, participant is not
     *                 considered withdrawn.
     * @param shouldMarkActivitiesAsReadOnly If true, client code will see
     * {@link ActivityInstance#isReadonly()} as true, regardless of underlying table value.
     *                                       If false, no override will be made.
     * @param isDSMVisible If true, DSM calls to get information about the participant should
     *                     return information.  If false, such calls should return not data.
     *
     */
    EnrollmentStatusType(boolean shouldReceiveCommunications,
                                 boolean isExited,
                                 boolean shouldMarkActivitiesAsReadOnly,
                                 boolean isDSMVisible) {
        this.shouldReceiveCommunications = shouldReceiveCommunications;
        this.isExited = isExited;
        this.shouldMarkActivitiesAsReadOnly = shouldMarkActivitiesAsReadOnly;
        this.isDSMVisible = isDSMVisible;
    }

    /**
     * Returns whether or not this status is considered to mean
     * exited from the study or withdrawn from the study.
     */
    public boolean isExited() {
        return isExited;
    }

    /**
     * Returns whether or not this status allows for communications
     * to the participant
     * @return true if the participant has opted-in to receiving communications, false otherwise.
     */
    public boolean shouldReceiveCommunications() {
        return shouldReceiveCommunications;
    }

    /**
     * Returns all statuses that should be included for
     * when DSM asks for information about a participant
     */
    public static List<EnrollmentStatusType> getDSMVisibleStates() {
        List<EnrollmentStatusType> dsmVisibleStates = new ArrayList<>(getAllStates());
        dsmVisibleStates.removeIf(state -> !state.isDSMVisible());
        return dsmVisibleStates;
    }

    public static List<EnrollmentStatusType> getAllStates() {
        return List.of(EnrollmentStatusType.values());
    }

    /**
     * Returns whether this status should result in clients seeing
     * activity instance as ready-only
     */
    public boolean shouldMarkActivitiesReadOnly() {
        return shouldMarkActivitiesAsReadOnly;
    }

    /**
     * Returns whether participants in this state should
     * be visible to DSM when DSM asks for participant data
     */
    public boolean isDSMVisible() {
        return isDSMVisible;
    }

    /**
     * Returns whether this status represents being enrolled in study or not.
     */
    public boolean isEnrolled() {
        return this == ENROLLED || this == COMPLETED;
    }
}
