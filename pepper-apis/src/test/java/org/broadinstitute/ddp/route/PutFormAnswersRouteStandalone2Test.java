package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.Set;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.junit.Test;

public class PutFormAnswersRouteStandalone2Test extends PutFormAnswersRouteStandaloneTestAbstract {

    @Test
    public void testSetStatusToCompleteForActivityInstanceWithIncompleteAnswers_Failure() {
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            long questionId = ((QuestionBlockDef) form.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli() - 10000,
                    user.getUserId(), "make required");
            handle.attach(QuestionDao.class).addRequiredRule(questionId, new RequiredRuleDef(null), revId);
            return insertNewInstanceAndDeferCleanup(handle, form.getActivityId());
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().put(urlTemplate).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET));

        TransactionWrapper.useTxn(handle -> {
            long questionId = ((QuestionBlockDef) form.getSections().get(0).getBlocks().get(0)).getQuestion().getQuestionId();
            RevisionMetadata meta = RevisionMetadata.now(user.getUserId(), "remove required");
            handle.attach(QuestionDao.class).disableRequiredRule(questionId, meta);
        });
    }

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
