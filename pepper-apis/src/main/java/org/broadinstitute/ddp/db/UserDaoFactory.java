package org.broadinstitute.ddp.db;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants;

public class UserDaoFactory {

    /**
     * Generate needed sequel queries.
     * @param sqlConfig sql configurations
     * @return an instance of UserDao
     */
    public static UserDao createFromSqlConfig(Config sqlConfig) {
        return new UserDao(sqlConfig.getString(ConfigFile.INSERT_USER_STMT),
                sqlConfig.getString(SqlConstants.FireCloud.HAD_ADMIN_ACCESS_QUERY),
                sqlConfig.getString(ConfigFile.PROFILE_EXISTS_QUERY),
                sqlConfig.getString(ConfigFile.GET_USER_ID_FROM_GUID),
                sqlConfig.getString(ConfigFile.USER_CLIENT_REVOCATION_QUERY),
                sqlConfig.getString(ConfigFile.PATCH_SEX_STMT),
                sqlConfig.getString(ConfigFile.PATCH_PREFERRED_LANGUAGE_STMT)
        );
    }
}
