package org.broadinstitute.dsm.db.jdbi;

import org.broadinstitute.dsm.db.dto.settings.UserSettingsDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserSettings extends SqlObject {
    @SqlUpdate ("UPDATE user_settings SET rows_on_page = :rowsOnPage WHERE user_id = :userId")
    int updateUserSettings(@Bind ("rowsOnPage") int rowsOnPage, @Bind ("userId") long userId);

    @SqlQuery ("SELECT rows_per_page FROM user_settings settings WHERE user_id = :userId")
    @RegisterConstructorMapper (UserSettingsDto.class)
    UserSettingsDto getUserSettingsFromUserId(@Bind ("userId") long userIdF);

    @SqlUpdate ("INSERT INTO user_settings SET user_id = :userId")
    @GetGeneratedKeys
    long insertNewUserSettings(@Bind ("userId") long userId);
}
