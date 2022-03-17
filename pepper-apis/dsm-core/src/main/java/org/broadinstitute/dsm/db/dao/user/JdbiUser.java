package org.broadinstitute.dsm.db.dao.user;

import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUser extends SqlObject {

    @SqlQuery ("SELECT user.user_id, user.name, user.email, user.phone_number FROM access_user user WHERE user.email = :userEmail")
    @RegisterConstructorMapper (UserDto.class)
    UserDto getUserByEmail(@Bind ("userEmail") String userEmail);

    @SqlQuery ("SELECT user.user_id, user.name, user.email, user.phone_number FROM access_user user WHERE user.user_id = :userId")
    @RegisterConstructorMapper (UserDto.class)
    long getUserByUserId(@Bind ("userId") long userId);

    @SqlUpdate ("INSERT INTO access_user (name, email) VALUES (:name,:email)")
    @GetGeneratedKeys
    long insert(@Bind ("name") String name, @Bind ("email") String email);

    default long insert(String email) {
        return insert(email, email);
    }

    @SqlQuery ("select * from user where guid = :guid")
    @RegisterConstructorMapper (UserDto.class)
    UserDto findByUserGuid(@Bind ("guid") String userGuid);

    @SqlUpdate ("DELETE FROM access_user WHERE user_id = :userId")
    int deleteByUserId(@Bind ("userId") long userId);

}
