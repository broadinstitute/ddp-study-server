package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiTempMailAddress extends SqlObject {

    @SqlUpdate("upsertTempAddress")
    @UseStringTemplateSqlLocator
    @GetGeneratedKeys
    Long saveTempAddress(@BindBean("a") MailAddress tempMailAddress,
                         @Bind("participantGuid") String participantGuid, @Bind("operatorGuid") String operatorGuid,
                         @Bind("activityInstanceGuid") String activityInstanceGuid);

    @SqlQuery("findTempAddressByActivityInstanceGuid")
    @RegisterBeanMapper(MailAddress.class)
    @UseStringTemplateSqlLocator
    Optional<MailAddress> findTempAddressByActvityInstanceGuid(
            @Bind("activityInstanceGuid") String activityInstanceGuid);

    @SqlUpdate("deleteTempAddressByActivityInstanceGuid")
    @UseStringTemplateSqlLocator
    int deleteTempAddressByActivityInstanceGuid(@Bind("activityInstanceGuid") String activityInstanceGuid);

    @SqlUpdate("deleteTempAddressByParticipantId")
    @UseStringTemplateSqlLocator
    int deleteTempAddressByParticipantId(@Bind("participantId") Long participantId);

}
