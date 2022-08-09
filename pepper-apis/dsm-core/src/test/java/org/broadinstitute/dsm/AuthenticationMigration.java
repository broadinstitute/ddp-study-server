package org.broadinstitute.dsm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.DdpDBUtils;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.jdbi.JdbiUser;
import org.broadinstitute.dsm.db.jdbi.JdbiUserRole;
import org.broadinstitute.dsm.db.jdbi.JdbiUserSettings;
import org.broadinstitute.dsm.exception.DaoException;
import org.broadinstitute.lddp.db.SimpleResult;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.junit.Before;
import org.junit.Test;

public class AuthenticationMigration {

    private static String clientKey;
    private static String auth0Domain;
    private static String SPACE = " ";

    @Before
    public void before() {
        TestHelper.setupDB();
        Config cfg = TestHelper.cfg;
        clientKey = cfg.getString("auth0.clientKey");
        auth0Domain = cfg.getString("auth0.account");
    }

    @Test
    public void migrateRoles() throws DaoException {
        List oldRoles = new ArrayList();
        SimpleResult result = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getAllRolesFromOldSchema();
            return dbVals;
        });
        oldRoles = (List) result.resultValue;
        List<String> finalOldRoles = oldRoles;
        TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            handle.attach(JdbiUserRole.class).insertOldRolesIntoPermissionTable(finalOldRoles);
            return dbVals;
        });
    }

    @Test
    public void migrateUsers() {
        List<UserDto> oldUsers = new ArrayList();
        SimpleResult result = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.createQuery("SELECT  user_id, name, email, phone_number from access_user where is_active = 1")
                    .registerRowMapper(ConstructorMapper.factory(UserDto.class))
                    .mapTo(UserDto.class)
                    .list();
            ;
            return dbVals;
        });
        oldUsers = (List) result.resultValue;
        for (UserDto user : oldUsers) {
            try {
                TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
                    SimpleResult dbVals = new SimpleResult();
                    int exists = handle.attach(JdbiUser.class).selectUserProfileByEMail(user.getEmail().orElseThrow());
                    if (exists > 0) {
                        return dbVals;
                    }
                    String userGuid = DdpDBUtils.uniqueUserGuid(handle);
                    String userHruid = DdpDBUtils.uniqueUserHruid(handle);

                    long now = Instant.now().toEpochMilli();
                    Long expiresAt = null;

                    long userId = handle.attach(JdbiUser.class).insertUser(auth0Domain, clientKey, null,
                            userGuid, userHruid, null, null, false, now, now, expiresAt, user.isActive());
                    if (user.getName().isEmpty() || user.getName().get().equals(user.getEmail())) {
                        user.setFirstName("");
                        user.setLastName("");
                    } else if (!user.getName().isEmpty()) {
                        String[] names = user.getName().get().split(SPACE);
                        user.setFirstName(names[0]);
                        if (names.length == 2) {
                            user.setLastName(names[1]);
                        }
                    }
                    String phone = user.getPhoneNumber().orElse("");
                    handle.attach(JdbiUser.class)
                            .insertUserProfile(Long.valueOf(userId), user.getFirstName(), user.getLastName(), phone,
                                    user.getEmail().orElseThrow());
                    handle.attach(JdbiUserSettings.class).insertNewUserSettings(userId);
                    System.out.println("Inserted " + user.getEmail().get() + " as userId " + userId + " into DSS database");
                    return dbVals;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
