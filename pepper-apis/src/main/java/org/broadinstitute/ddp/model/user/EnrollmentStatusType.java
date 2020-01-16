package org.broadinstitute.ddp.model.user;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;

public enum EnrollmentStatusType {

    REGISTERED(true, false, false, true),
    ENROLLED(true, false, false,  true),
    EXITED_BEFORE_ENROLLMENT(false, true, true, false),
    EXITED_AFTER_ENROLLMENT(false, true, true, false),
    CONSENT_SUSPENDED(true, false, true, true);

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
    private EnrollmentStatusType(boolean shouldReceiveCommunications,
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
     * @return
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
}
