package org.broadinstitute.ddp.db.dao;

import java.time.LocalDate;
import java.util.Optional;

import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;

public class UserProfileCachedSql extends SQLObjectWrapper<UserProfileSql> implements UserProfileSql {

    public UserProfileCachedSql(Handle handle) {
        super(handle, UserProfileSql.class);
    }

    @Override
    public long insert(long userId, String firstName, String lastName, UserProfile.SexType sexType, LocalDate birthDate,
                       Long preferredLangId, Boolean doNotContact, Boolean isDeceased) {
        var id = target.insert(userId, firstName, lastName, sexType, birthDate, preferredLangId, doNotContact, isDeceased);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return id;
    }

    @Override
    public boolean upsertFirstName(long userId, String firstName) {
        var val = target.upsertFirstName(userId, firstName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;

    }

    @Override
    public boolean upsertLastName(long userId, String lastName) {
        var val = target.upsertLastName(userId, lastName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public boolean upsertBirthDate(long userId, LocalDate birthDate) {
        var val = target.upsertBirthDate(userId, birthDate);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int update(long userId, String firstName, String lastName, UserProfile.SexType sexType, LocalDate birthDate,
                      Long preferredLangId, Boolean doNotContact, Boolean isDeceased) {
        var val = target.update(userId, firstName, lastName, sexType, birthDate, preferredLangId, doNotContact, isDeceased);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateFirstName(long userId, String firstName) {
        var val = target.updateFirstName(userId, firstName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateLastName(long userId, String lastName) {
        var val = target.updateLastName(userId, lastName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateDoNotContact(long userId, Boolean doNotContact) {
        var val = target.updateDoNotContact(userId, doNotContact);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updatePreferredLangId(long userId, long preferredLangId) {
        var val = target.updatePreferredLangId(userId, preferredLangId);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int deleteByUserId(long userId) {
        var val = target.deleteByUserId(userId);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int deleteByUserGuid(String userGuid) {
        Optional<User> userOpt = getHandle().attach(UserDao.class).findUserByGuid(userGuid);
        var val = target.deleteByUserGuid(userGuid);
        userOpt.ifPresent(user -> notifyModelUpdated(ModelChangeType.USER, user.getId()));
        return val;
    }
}
