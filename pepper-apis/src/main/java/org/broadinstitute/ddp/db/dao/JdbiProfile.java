package org.broadinstitute.ddp.db.dao;

import java.time.LocalDate;

import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiProfile {

    String MAIN_SELECT = "select up.user_id, up.first_name, up.last_name, up.sex,"
            + " up.birth_date, up.preferred_language_id,"
            + " (select iso_language_code from language_code where language_code_id = up.preferred_language_id) as iso_language_code,"
            + " up.do_not_contact from user u, user_profile up"
            + " where u.user_id = up.user_id";

    @SqlUpdate("createUserProfileStmt")
    @UseStringTemplateSqlLocator
    int insert(@BindBean UserProfileDto userProfileDto);

    @SqlUpdate("delete from user_profile where user_id = :userId")
    int deleteByUserId(@Bind("userId") Long userId);

    @SqlQuery(MAIN_SELECT + " and u.guid = :userGuid")
    @RegisterConstructorMapper(UserProfileDto.class)
    UserProfileDto getUserProfileByUserGuid(@Bind("userGuid") String userGuid);

    @SqlQuery(MAIN_SELECT + " and u.user_id = :userId")
    @RegisterConstructorMapper(UserProfileDto.class)
    UserProfileDto getUserProfileByUserId(@Bind("userId") Long userId);

    @SqlUpdate("update user_profile set first_name = :firstName where user_id = :userId")
    int updateFirstName(@Bind("firstName") String firstName, @Bind("userId") long userId);

    @SqlUpdate("update user_profile set last_name = :lastName where user_id = :userId")
    int updateLastName(@Bind("lastName") String lastName, @Bind("userId") long userId);

    @SqlUpdate("update user_profile set do_not_contact = :doNotContact where user_id = :userId")
    int updateDoNotContact(@Bind("doNotContact") Boolean doNotContact, @Bind("userId") long userId);

    @SqlUpdate("update user_profile set preferred_language_id = :langId where user_id = :userId")
    int updatePreferredLangId(@Bind("userId") long userId, @Bind("langId") long preferredLangId);

    @SqlUpdate("INSERT INTO user_profile (user_id, first_name) VALUES (:userId, :firstName)"
            + " ON DUPLICATE KEY UPDATE first_name = VALUES(first_name)")
    boolean upsertFirstName(@Bind("userId") long userId, @Bind("firstName") String firstName);

    @SqlUpdate("INSERT INTO user_profile (user_id, last_name) VALUES (:userId, :lastName)"
            + " ON DUPLICATE KEY UPDATE last_name = VALUES(last_name)")
    boolean upsertLastName(@Bind("userId") long userId, @Bind("lastName") String lastName);

    @SqlUpdate("insert into user_profile (user_id, birth_date) values (:userId, :birthDate)"
            + " on duplicate key update birth_date = values(birth_date)")
    boolean upsertBirthDate(@Bind("userId") long userId, @Bind("birthDate") LocalDate birthDate);
}
