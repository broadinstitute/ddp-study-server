package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface UserProfileDao extends SqlObject {

    @CreateSqlObject
    UserProfileSql getUserProfileSql();

    @GetGeneratedKeys
    default Long createProfile(UserProfile profile) {
        return getUserProfileSql().insert(
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getSexType(),
                profile.getBirthDate(),
                profile.getPreferredLangId(),
                profile.getTimeZone(),
                profile.getDoNotContact(),
                profile.getIsDeceased(),
                profile.getSkipLanguagePopup());
    }

    default UserProfile updateProfile(UserProfile profile) {
        int numUpdated = getUserProfileSql().update(
                profile.getUserId(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getSexType(),
                profile.getBirthDate(),
                profile.getPreferredLangId(),
                profile.getTimeZone(),
                profile.getDoNotContact(),
                profile.getIsDeceased(),
                profile.getSkipLanguagePopup());
        DBUtils.checkUpdate(1, numUpdated);
        return findProfileByUserId(profile.getUserId())
                .orElseThrow(() -> new DaoException("Could not find profile for use id " + profile.getUserId()));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("findProfileByUserId")
    @RegisterConstructorMapper(UserProfile.class)
    Optional<UserProfile> findProfileByUserId(@Bind("userId") long userId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findProfileByUserGuid")
    @RegisterConstructorMapper(UserProfile.class)
    Optional<UserProfile> findProfileByUserGuid(@Bind("userGuid") String userGuid);
}
