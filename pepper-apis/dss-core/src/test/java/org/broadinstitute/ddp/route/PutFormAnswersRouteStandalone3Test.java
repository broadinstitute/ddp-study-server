package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.junit.Test;

/**
 * The test moved to a separate class because result of other tests affected on it and
 * it's behaviour was incorrect
 */
public class PutFormAnswersRouteStandalone3Test extends PutFormAnswersRouteStandaloneTestAbstract {

    @Test
    public void testStudyAdmin_hiddenInstance() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto dto = insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(testData.getUserId(), true, Set.of(form.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
            return dto;
        });

        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", instanceDto.getGuid())
                    .when().put(urlTemplate).then().assertThat()
                    .log().all()
                    .statusCode(200);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }
}
