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
                sqlConfig.getString(ConfigFile.UPSERT_USER_PROFILE_FIRST_AND_LAST_NAME),
                sqlConfig.getString(ConfigFile.CHECK_USER_GUID_QUERY),
                sqlConfig.getString(ConfigFile.INSERT_GOVERNED_PARTICIPANT),
                sqlConfig.getString(SqlConstants.FireCloud.HAD_ADMIN_ACCESS_QUERY),
                sqlConfig.getString(ConfigFile.USER_EXISTS_QUERY),
                sqlConfig.getString(ConfigFile.USER_GUID_FOR_AUTH0ID_QUERY),
                sqlConfig.getString(ConfigFile.PROFILE_EXISTS_QUERY),
                sqlConfig.getString(ConfigFile.GET_USER_ID_FROM_GUID),
                sqlConfig.getString(ConfigFile.GET_USER_ID_FROM_HRUID),
                sqlConfig.getString(ConfigFile.SqlQuery.USER_GUID_BY_ID),
                sqlConfig.getString(ConfigFile.USER_EXISTS_GUID),
                sqlConfig.getString(ConfigFile.GOVERNANCE_ALIAS_EXISTS_QUERY),
                sqlConfig.getString(ConfigFile.USER_CLIENT_REVOCATION_QUERY),
                sqlConfig.getString(ConfigFile.GET_ALL_GOV_PARTS_QUERY),
                sqlConfig.getString(ConfigFile.PATCH_SEX_STMT),
                sqlConfig.getString(ConfigFile.PATCH_PREFERRED_LANGUAGE_STMT)
        );
    }
}
