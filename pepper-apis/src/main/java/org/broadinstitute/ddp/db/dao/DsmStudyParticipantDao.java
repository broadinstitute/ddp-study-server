package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.dsm.DsmStudyParticipant;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface DsmStudyParticipantDao extends SqlObject {

    @SqlQuery("selectParticipantInfo")
    @UseStringTemplateSqlLocator
    @RegisterBeanMapper(DsmStudyParticipant.class)
    Optional<DsmStudyParticipant> findStudyParticipant(@Bind("userGuidOrAltPid") String userGuidOrAltPid,
                                                       @Bind("studyGuid") String studyGuid,
                                                       @BindList("statuses") List<EnrollmentStatusType> allowedEnrollmentStatuses);
}
