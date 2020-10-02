package org.broadinstitute.ddp.db.dao;

import java.time.LocalDate;
import java.time.ZoneId;

import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserProfileSql extends SqlObject {

    //
    // inserts
    //

    @GetGeneratedKeys
    @SqlUpdate("insert into user_profile (user_id, first_name, last_name, sex, birth_date,"
            + "        preferred_language_id, time_zone, do_not_contact, is_deceased, skip_language_popup)"
            + " values (:userId, :firstName, :lastName, :sex, :birthDate,"
            + " :langId, :tz, :doNotContact, :isDeceased, :skipLanguagePopup)")
    long insert(@Bind("userId") long userId,
                @Bind("firstName") String firstName,
                @Bind("lastName") String lastName,
                @Bind("sex") UserProfile.SexType sexType,
                @Bind("birthDate") LocalDate birthDate,
                @Bind("langId") Long preferredLangId,
                @Bind("tz") ZoneId timeZone,
                @Bind("doNotContact") Boolean doNotContact,
                @Bind("isDeceased") Boolean isDeceased,
                @Bind("skipLanguagePopup") Boolean skipLanguagePopup);

    //
    // upserts
    //

    @SqlUpdate("insert into user_profile (user_id, first_name) values (:userId, :firstName)"
            + " on duplicate key update first_name = values(first_name)")
    boolean upsertFirstName(@Bind("userId") long userId, @Bind("firstName") String firstName);

    @SqlUpdate("insert into user_profile (user_id, last_name) values (:userId, :lastName)"
            + " on duplicate key update last_name = values(last_name)")
    boolean upsertLastName(@Bind("userId") long userId, @Bind("lastName") String lastName);

    @SqlUpdate("insert into user_profile (user_id, birth_date) values (:userId, :birthDate)"
            + " on duplicate key update birth_date = values(birth_date)")
    boolean upsertBirthDate(@Bind("userId") long userId, @Bind("birthDate") LocalDate birthDate);

    //
    // updates
    //

    @SqlUpdate("update user_profile"
            + "    set first_name = :firstName, last_name = :lastName, sex = :sex, birth_date = :birthDate,"
            + "        preferred_language_id = :langId, do_not_contact = :doNotContact, is_deceased = :isDeceased,"
            + "        skip_language_popup = :skipLanguagePopup, time_zone = :tz"
            + "  where user_id = :userId")
    int update(@Bind("userId") long userId,
               @Bind("firstName") String firstName,
               @Bind("lastName") String lastName,
               @Bind("sex") UserProfile.SexType sexType,
               @Bind("birthDate") LocalDate birthDate,
               @Bind("langId") Long preferredLangId,
               @Bind("tz") ZoneId timeZone,
               @Bind("doNotContact") Boolean doNotContact,
               @Bind("isDeceased") Boolean isDeceased,
               @Bind("skipLanguagePopup") Boolean skipLanguagePopup);

    @SqlUpdate("update user_profile set first_name = :firstName where user_id = :userId")
    int updateFirstName(@Bind("userId") long userId, @Bind("firstName") String firstName);

    @SqlUpdate("update user_profile set last_name = :lastName where user_id = :userId")
    int updateLastName(@Bind("userId") long userId, @Bind("lastName") String lastName);

    @SqlUpdate("update user_profile set do_not_contact = :doNotContact where user_id = :userId")
    int updateDoNotContact(@Bind("userId") long userId, @Bind("doNotContact") Boolean doNotContact);

    @SqlUpdate("update user_profile set preferred_language_id = :langId where user_id = :userId")
    int updatePreferredLangId(@Bind("userId") long userId, @Bind("langId") long preferredLangId);

    //
    // deletes
    //

    @SqlUpdate("delete from user_profile where user_id = :userId")
    int deleteByUserId(@Bind("userId") long userId);

    @SqlUpdate("delete from user_profile where user_id = (select user_id from user where guid = :userGuid)")
    int deleteByUserGuid(@Bind("userGuid") String userGuid);
}
