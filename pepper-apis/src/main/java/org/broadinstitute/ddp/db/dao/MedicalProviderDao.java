package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface MedicalProviderDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(MedicalProviderDao.class);

    @CreateSqlObject
    JdbiMedicalProvider getJdbiMedicalProvider();

    @CreateSqlObject
    JdbiUserStudyEnrollment getJdbiUserStudyEnrollment();

    default int insert(MedicalProviderDto medicalProviderDto) {
        updateCompletedDateIfAlreadyCompleted(medicalProviderDto);
        return (getJdbiMedicalProvider().insert(medicalProviderDto));
    }

    default int updateByGuid(MedicalProviderDto medicalProviderDto) {
        updateCompletedDateIfAlreadyCompleted(medicalProviderDto);
        return (getJdbiMedicalProvider().updateByGuid(medicalProviderDto));
    }

    default int deleteByGuid(String medicalProviderGuid) {
        Optional<MedicalProviderDto> result = getJdbiMedicalProvider().getByGuid(medicalProviderGuid);
        if (result.isPresent()) {
            MedicalProviderDto medicalProviderDto = result.get();
            updateCompletedDateIfAlreadyCompleted(medicalProviderDto);
            return (getJdbiMedicalProvider().deleteByGuid(medicalProviderGuid));
        }

        return 0; // We couldn't find it to delete it!
    }

    default int deleteById(long id) {
        Optional<MedicalProviderDto> result = getJdbiMedicalProvider().getById(id);
        if (result.isPresent()) {
            MedicalProviderDto medicalProviderDto = result.get();
            updateCompletedDateIfAlreadyCompleted(medicalProviderDto);
            return (getJdbiMedicalProvider().deleteById(id));
        }

        return 0; // We couldn't find it to delete it!
    }

    default void updateCompletedDateIfAlreadyCompleted(MedicalProviderDto medicalProviderDto) {
        // Only modify completion time if they already completed
        long studyId = medicalProviderDto.getUmbrellaStudyId();
        long userId = medicalProviderDto.getUserId();
        Optional<EnrollmentStatusType> enrollmentStatus = getJdbiUserStudyEnrollment()
                .getEnrollmentStatusByUserAndStudyIds(userId, studyId);
        if (enrollmentStatus.isPresent()
                && enrollmentStatus.get() == EnrollmentStatusType.ENROLLED
                && !medicalProviderDto.isBlank()) {
            getJdbiUserStudyEnrollment()
                    .changeUserStudyEnrollmentStatus(userId, studyId, EnrollmentStatusType.ENROLLED);

            int numQueued = getHandle().attach(EventDao.class).addMedicalUpdateTriggeredEventsToQueue(studyId, userId);
            LOG.info("Queued {} medical-update events for participantId={} studyId={}", numQueued, userId, studyId);
        }
    }
}
