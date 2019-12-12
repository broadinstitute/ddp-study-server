package org.broadinstitute.ddp.model.study;

import java.util.List;

import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;

public class EnrollmentStatusCount {

    private int participantCount;
    private int registeredCount;

    public EnrollmentStatusCount(int participantCount, int registeredCount) {
        this.participantCount = participantCount;
        this.registeredCount = registeredCount;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public static EnrollmentStatusCount getEnrollmentStatusCountByEnrollments(List<EnrollmentStatusDto> enrollments) {
        // Given this might be called by a large number of clients, the results of this should probably
        // either be cached or rolled into a more optimized query
        int participantCount = 0;
        int registeredCount = 0;
        for (EnrollmentStatusDto enrollment : enrollments) {
            switch (enrollment.getEnrollmentStatus()) {
                case ENROLLED:
                    ++participantCount;
                    break;
                case REGISTERED:
                    ++registeredCount;
                    break;
                default:
                    break;
            }
        }

        return new EnrollmentStatusCount(participantCount, registeredCount);
    }
}
