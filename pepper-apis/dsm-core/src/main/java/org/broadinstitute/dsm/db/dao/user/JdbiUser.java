package org.broadinstitute.dsm.db.dao.user;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUser extends SqlObject {

    @SqlQuery ("SELECT u.guid, u.user_id, concat(p.first_name, \" \", p.last_name) as name, p.email, p.phone as phoneNumber FROM user u left join user_profile p on (u.user_id = p.user_id)  WHERE p.email = :userEmail")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByEmail(@Bind ("userEmail") String userEmail);

    @SqlQuery ("SELECT up.user_id, up.first_name, up.last_name, up.email, up.phone_number FROM user_profile up WHERE up.user_id = :userId")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByUserId(@Bind ("userId") long userId);

    @SqlUpdate ("")
    @GetGeneratedKeys
    long insert(@Bind ("name") String name, @Bind ("email") String email);

    default long insert(String email) {
        return insert(email, email);//todo pegah add to both user profile and user
    }

    @SqlQuery ("select guid from user u " +
            "left join user_profile up on (u.user_id = up.user_id)" +
            "where up.email = :email")
    String findUserGUID(@Bind ("email") String email);

    @SqlQuery ("select * from user where guid = :guid")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByUserGUID(@Bind ("guid") String userGuid);

    @SqlUpdate ("")
    int deleteByUserId(@Bind ("userId") long userId);

    @SqlQuery (" SELECT user_id, first_name, last_name, phone as phoneNumber, email FROM user_profile")
    @RegisterConstructorMapper (ArrayList.class)
    ArrayList<UserDto> getUserMap();

    @SqlQuery ("SELECT p.name FROM user_role ur " +
            "left join user u on (u.user_id = ur.user_id) " +
            "left join role_permissions rp on (rp.role_id = ur.role_id) " +
            "left join permissions p on (p.permissions_id = rp.permissions_id) " +
            "where u.user_id = :userId")
    List<String> getAllUserPermissions(@Bind ("userId") long userId);

}
