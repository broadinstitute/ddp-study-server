package org.broadinstitute.dsm.db.jdbi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUser extends SqlObject {

    @SqlQuery (
            "SELECT u.guid, u.user_id, concat(p.first_name, \" \", p.last_name) as name, p.email, p.phone as phoneNumber, " +
                    "u.auth0_user_id, p.first_name, p.last_name, u.hruid as shortId, u.is_active, u.dsm_legacy_id " +
                    "FROM user u left join user_profile p on (u.user_id = p.user_id)  WHERE p.email = :userEmail and u.is_active = 1")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByEmail(@Bind ("userEmail") String userEmail);

    @SqlQuery ("SELECT up.user_id, up.first_name, up.last_name, up.email, up.phone_number FROM user_profile up WHERE up.user_id = :userId")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByUserId(@Bind ("userId") long userId);

    @SqlQuery ("select guid from user u " +
            "left join user_profile up on (u.user_id = up.user_id)" +
            "where up.email = :email")
    String findUserGUID(@Bind ("email") String email);

    @SqlQuery ("select * from user where guid = :guid")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByUserGUID(@Bind ("guid") String userGuid);

    @SqlUpdate ("")
    int deleteByUserId(@Bind ("userId") long userId);

    @SqlQuery (" SELECT user_id, first_name, last_name, phone as phoneNumber, email, hruid as shortId FROM user_profile")
    @RegisterConstructorMapper (ArrayList.class)
    ArrayList<UserDto> getUserMap();

    @SqlQuery ("SELECT p.name FROM user_role ur " +
            "left join user u on (u.user_id = ur.user_id) " +
            "left join role_permissions rp on (rp.role_id = ur.role_id) " +
            "left join permissions p on (p.permissions_id = rp.permissions_id) " +
            "where u.user_id = :userId and u.is_active = 1")
    List<String> getAllUserPermissions(@Bind ("userId") long userId);

    @GetGeneratedKeys
    @SqlUpdate ("insert into user (created_by_client_id, auth0_tenant_id, auth0_user_id," +
            "       guid, hruid, legacy_altpid, legacy_shortid, is_locked, created_at, updated_at, expires_at, is_active)" +
            " select c.client_id, t.auth0_tenant_id, :auth0UserId," +
            "       :guid, :hruid, :legacyAltPid, :legacyShortId, :isLocked, :createdAt, :updatedAt, :expiresAt, :isActive" +
            " from auth0_tenant as t" +
            " join client as c on c.auth0_tenant_id = t.auth0_tenant_id" +
            " where t.auth0_domain = :auth0Domain" +
            " and c.auth0_client_id = :auth0ClientId")
    long insertUser(
            @Bind ("auth0Domain") String auth0Domain,
            @Bind ("auth0ClientId") String auth0ClientId,
            @Bind ("auth0UserId") String auth0UserId,
            @Bind ("guid") String guid,
            @Bind ("hruid") String hruid,
            @Bind ("legacyAltPid") String legacyAltPid,
            @Bind ("legacyShortId") String legacyShortId,
            @Bind ("isLocked") boolean isLocked,
            @Bind ("createdAt") long createdAt,
            @Bind ("updatedAt") long updatedAt,
            @Bind ("expiresAt") Long expiresAt,
            @Bind ("isActive") boolean isActive);

    @SqlUpdate ("INSERT INTO user_profile (user_id, first_name, last_name, phone, email) values (:userId, :firstName, :lastName, :phone, :email)")
    void insertUserProfile(@Bind ("userId") long userId, @Bind ("firstName") String firstName, @Bind ("lastName") String lastName,
                           @Bind ("phone") String phone, @Bind ("email") String email);

    @SqlQuery ("Select count(*) from user_profile where email = :email")
    int selectUserProfileByEMail(@Bind ("email") String email);

    @SqlQuery ("Select user_id from user_profile where email = :email")
    Optional<Long> selectUserIdByEMail(@Bind ("email") String email);

    @SqlUpdate ("INSERT INTO study_admin (user_id, umbrella_study_id) values (:userId, :studyId)")
    @GetGeneratedKeys
    long insertStudyAdmin();

    @SqlUpdate ("UPDATE user SET auth0_user_id = :auth0UserId WHERE user_id = :userId")
    int updateAuth0UserId(@Bind ("userId") long userId, @Bind ("auth0UserId") String auth0UserId);

    @SqlUpdate ("UPDATE user_profile SET first_name = :firstName, last_name = :lastName WHERE user_id = :userId")
    int modifyUser(@Bind ("userId") long userId, @Bind ("firstName") String firstName, @Bind ("lastName") String lastName);

    @SqlUpdate ("UPDATE user SET is_active = 0 WHERE user_id = :userId")
    int deactivateUser(@Bind ("userId") long userId);

}
