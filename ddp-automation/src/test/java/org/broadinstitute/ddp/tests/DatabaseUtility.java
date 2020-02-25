package org.broadinstitute.ddp.tests;

import java.util.List;
import java.util.Optional;

import com.auth0.jwt.JWT;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAnswer;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.pages.GatekeeperPage;
import org.broadinstitute.ddp.util.JDITestUtils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseUtility {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtility.class);
    private static final int STATUS_FOUND = 1;
    private static final String ANGIO_STUDY_GUID = "ANGIO";
    private static final String BRAIN_STUDY_GUID = "cmi-brain";
    private static final String IRB_PASSWORD = "broad_institute";

    /**
     * Get the user's guid from the token in localStorage
     *
     * @return ddpUserGuid
     */
    private static String getUserGuid() {
        String jwtToken = JDITestUtils.waitUntilScriptIsExecuted(JavaScriptScriptConstants.GET_LOGIN_TOKEN_JS);
        logger.info("Token: {}", jwtToken);
        String ddpUserGuid = JWT.decode(jwtToken).getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
        return ddpUserGuid;
    }

    /**
     * Get the user id that is used in tables in the backend to denote data that belongs to a user.
     * Uses the guid guid to get the token, do not need to pass anything in.
     *
     * @return user id
     */
    private static long getUserId() {
        long id = TransactionWrapper.withTxn(handle -> {
            JdbiUser userDao = handle.attach(JdbiUser.class);
            long userId = userDao.getUserIdByGuid(getUserGuid());
            logger.info("User ID: {}", userId);
            return userId;
        });
        return id;
    }


    /**
     * Reads the token out of browser local storage
     * and verifies that the userid in the token exists
     * in the database.
     */
    public static void verifyUserExists() {
        TransactionWrapper.withTxn(handle -> {
            long userId = getUserId();
            Assert.assertTrue(userId > 0);
            logger.info("Verified user id {} for user guid {}", userId, getUserGuid());
            return null;
        });
    }

    public static void checkUserSignedUpForMailingList(String email) {
        // where is this initialized
        TransactionWrapper.withTxn(handle -> {
            JdbiMailingList mailingListDao = handle.attach(JdbiMailingList.class);
            Optional<Long> userId = null;

            if (BaseTest.currentPageIsAngioWebsite()) {
                userId = mailingListDao.findIdByEmailAndStudyGuid(email, ANGIO_STUDY_GUID);
            } else if (BaseTest.currentPageIsBrainWebsite()) {
                userId = mailingListDao.findIdByEmailAndStudyGuid(email, BRAIN_STUDY_GUID);
            }

            if (userId != null && userId.isPresent()) {
                logger.info("User {} has successfully been added to the mailing list", email);
            } else {
                logger.info("User {} was not found in the mailing list", email);
            }

            /*if (emailStatus != STATUS_FOUND) {
                for (MailingListEntryDto mailingListEntry : mailingList) {
                    logger.info("Email: {}", mailingListEntry.getEmail());
                }
            } else {
                logger.info("{} was found in mailing list", email);
            }*/
            return null;
        });
    }

    public static void unlockGatekeeperPage(GatekeeperPage gatekeeperPage) {
        /*TransactionWrapper.withTxn(handle -> {
            //Database connection needs some work - connection related failures concerning TransactionWrapper
            JdbiUmbrellaStudy umbrellaStudyDao = handle.attach(JdbiUmbrellaStudy.class);
            String password = umbrellaStudyDao.getIrbPasswordUsingStudyGuid(ANGIO_STUDY_GUID);
            logger.info("Password: {}", password);
            gatekeeperPage.setPassword(IRB_PASSWORD);
            return null;

        });*/
        gatekeeperPage.setPassword(IRB_PASSWORD);
    }

    private static void listResultSet(List<Long> resultSet) {
        for (Long result : resultSet) {
            logger.info("\tResult: {}", result);
        }
        logger.info("Size of result set: {}", resultSet.size());
    }

    public static List<Long> getAnswerIds() {
        List<Long> ids = TransactionWrapper.withTxn(handle -> {
            logger.info("Using user id: {}", getUserId());
            JdbiAnswer answerDao = handle.attach(JdbiAnswer.class);
            return answerDao.getAnswerIds(getUserId());
        });
        logger.info("Answer IDs:");
        listResultSet(ids);
        return ids;
    }
}
