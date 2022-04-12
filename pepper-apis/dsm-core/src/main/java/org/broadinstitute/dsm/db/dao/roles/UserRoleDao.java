package org.broadinstitute.dsm.db.dao.roles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dto.user.AssigneeDto;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRoleDao {
    private static final Logger logger = LoggerFactory.getLogger(UserRoleDao.class);

    public static Collection<String> getListOfAllowedRealms(@NonNull long userId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getListOfAllowedRealmsGuids(userId);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms for userId " + userId, results.resultException);
        }
        logger.info("found " + ((Collection<String>) results.resultValue).size() + " realms for user " + userId);
        List allowedStudies = new ArrayList((Collection<String>) results.resultValue);
        SimpleResult finalResults = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getAllowedStudiesNames(allowedStudies);
            return dbVals;
        });
        return (Collection<String>) finalResults.resultValue;
    }

    public static List<NameValue> getAllowedStudiesNameValues(@NonNull String userId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getListOfAllowedRealmsGuids(Long.parseLong(userId));
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of realms for userId " + userId, results.resultException);
        }
        logger.info("found " + ((Collection<String>) results.resultValue).size() + " studies for user " + userId);
        List allowedStudies = (List<String>) results.resultValue;
        SimpleResult finalResults = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getAllowedStudiesNameVale(allowedStudies);
            return dbVals;
        });
        return (List<NameValue>) finalResults.resultValue;
    }

    public List<String> checkUserAccess(@NonNull long userId) {
        List<String> permissions;
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getPermissionsForUser(userId);
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of permissions for userId " + userId, results.resultException);
        }

        permissions = (List<String>) results.resultValue;
        return permissions;
    }

    public ArrayList<String> getUserPermissionsForUserEmail(@NonNull String email) {
        ArrayList<String> permissions;
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getPermissionsForUserEmail(email);
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of permissions for user with email " + email, results.resultException);
        }

        permissions = (ArrayList<String>) results.resultValue;
        return permissions;
    }

    public List<String> getUserRolesPeRealm(long userId, String study) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserRole.class).getUserRolesPerRealm(userId, study);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by id ", results.resultException);
        }
        return (List<String>) results.resultValue;
    }

    public static HashMap<Long, AssigneeDto> getAssigneeMap(String realm) {
        HashMap<Long, AssigneeDto> assignees = new HashMap<>();
        TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            List<AssigneeDto> assigneeLists = handle.attach(JdbiUserRole.class).getAssigneesForStudy(realm);
            for (AssigneeDto assigneeDto : assigneeLists) {
                assignees.put(assigneeDto.getAssigneeId(), new AssigneeDto(assigneeDto.getAssigneeId(), assigneeDto.getName().orElse(""),
                        assigneeDto.getEmail().orElseThrow()));
            }
            return null;
        });

        logger.info("Found " + assignees.size() + " assignees ");
        return assignees;
    }

    public static Collection<AssigneeDto> getAssignees(String realm) {
        return UserRoleDao.getAssigneeMap(realm).values();
    }
}
