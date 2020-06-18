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
        var id = delegate.insert(userId, firstName, lastName, sexType, birthDate, preferredLangId, doNotContact, isDeceased);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return id;
    }

    @Override
    public boolean upsertFirstName(long userId, String firstName) {
        var val = delegate.upsertFirstName(userId, firstName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;

    }

    @Override
    public boolean upsertLastName(long userId, String lastName) {
        var val = delegate.upsertLastName(userId, lastName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public boolean upsertBirthDate(long userId, LocalDate birthDate) {
        var val = delegate.upsertBirthDate(userId, birthDate);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int update(long userId, String firstName, String lastName, UserProfile.SexType sexType, LocalDate birthDate,
                      Long preferredLangId, Boolean doNotContact, Boolean isDeceased) {
        var val = delegate.update(userId, firstName, lastName, sexType, birthDate, preferredLangId, doNotContact, isDeceased);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateFirstName(long userId, String firstName) {
        var val = delegate.updateFirstName(userId, firstName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateLastName(long userId, String lastName) {
        var val = delegate.updateLastName(userId, lastName);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updateDoNotContact(long userId, Boolean doNotContact) {
        var val = delegate.updateDoNotContact(userId, doNotContact);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int updatePreferredLangId(long userId, long preferredLangId) {
        var val = delegate.updatePreferredLangId(userId, preferredLangId);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int deleteByUserId(long userId) {
        var val = delegate.deleteByUserId(userId);
        notifyModelUpdated(ModelChangeType.USER, userId);
        return val;
    }

    @Override
    public int deleteByUserGuid(String userGuid) {
        Optional<User> userOpt = getHandle().attach(UserDao.class).findUserByGuid(userGuid);
        var val = delegate.deleteByUserGuid(userGuid);
        userOpt.ifPresent(user -> notifyModelUpdated(ModelChangeType.USER, user.getId()));
        return val;
    }
}
