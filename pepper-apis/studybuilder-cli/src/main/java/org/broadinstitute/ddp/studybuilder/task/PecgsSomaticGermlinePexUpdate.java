package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class PecgsSomaticGermlinePexUpdate implements CustomTask {

    public static String CURR_PEX = "user.studies[\"CMI-OSTEO\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions"
            + "[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n"
            + " && ((operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && operator.studies[\"cmi-osteo\"]."
            + "forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7)\n"
            + " || (user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"]."
            + "questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7))";

    public static String NEW_OSTEO_GERMLINE_PEX = "user.studies[\"CMI-OSTEO\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions"
            + "[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n"
            + " (( user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].hasInstance() && user.studies[\"CMI-OSTEO\"]"
            + ".forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS))"
            + " || ( user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].hasInstance() && user.studies[\"CMI-OSTEO\"]."
            + "forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS)))";

    public static String NEW_LMS_GERMLINE_PEX = "user.studies[\"cmi-lms\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions"
            + "[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n"
            + " (( user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].hasInstance() && user.studies[\"cmi-lms\"]"
            + ".forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS))"
            + " || ( user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].hasInstance() && user.studies[\"cmi-lms\"]."
            + "forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS)))";

    public static String NEW_LMS_SOMATIC_PEX = ""
            + "!(operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"].answers.hasOption(\"CA\") || "
            + " operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"].answers.hasOption(\"NY\")) && "
            + " (( user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].hasInstance() && "
            + " user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_TISSUE\"].answers.hasTrue() && "
            + " user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS))"
            + " || ( user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].hasInstance() "
            + " && user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_TISSUE\"].answers.hasTrue() && "
            + " user.studies[\"cmi-lms\"].forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_CHILD_DOB\"].answers.ageAtLeast(7, YEARS)))";

    public static String NEW_OSTEO_SOMATIC_PEX =
            "      (((operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance()\n"
                    + "          && !(\n"
                    + "  operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"].answers.hasOption(\"CA\") \n"
                    + "   || operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"].answers.hasOption(\"NY\")\n"
                    + "   ))\n"
                    + "   ||\n"
                    + "   (user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance()\n"
                    + "  && !(\n"
                    + "  user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"].answers.hasOption(\"CA\") \n"
                    + "  || user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"].answers.hasOption(\"NY\")\n"
                    + "  )))\n"
                    + "  &&\n"
                    + " (( user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].hasInstance() && "
                    + " user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_CHILD_DOB\"]."
                    + "answers.ageAtLeast(7, YEARS))\n"
                    + "            || ( user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].hasInstance() && "
                    + " user.studies[\"CMI-OSTEO\"].forms[\"PARENTAL_CONSENT\"].questions[\"PARENTAL_CONSENT_CHILD_DOB\"]"
                    + ".answers.ageAtLeast(7, YEARS)))\n"
                    + "        )      \n ";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        ///not used
    }

    @Override
    public void run(Handle handle) {
        updatePediatricGermlinePex(handle);
    }

    public void updatePediatricGermlinePex(Handle handle) {

        //update matched OSTEO content block pex
        List<Long> matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getPexIdToUpd();
        log.info("Matched Osteo {} pex expressions .. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_OSTEO_GERMLINE_PEX);

        //update OSTEO Question pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getQuestionPexIdByStudyAnsQuestions(
                "CMI-OSTEO", "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC",
                Arrays.asList("ADDENDUM_CONSENT_PATIENT_DOB_PEDIATRIC", "ADDENDUM_CONSENT_PATIENT_SINGATURE_PEDIATRIC"));
        log.info("Matched Osteo {} question pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_OSTEO_GERMLINE_PEX);

        //update matched LMS content block pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getLmsPexIdToUpd();
        log.info("Matched LMS {} pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_LMS_GERMLINE_PEX);

        //update LMS Question pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getQuestionPexIdByStudyAnsQuestions(
                "cmi-lms", "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC",
                Arrays.asList("ADDENDUM_CONSENT_PATIENT_DOB_PEDIATRIC", "ADDENDUM_CONSENT_PATIENT_SINGATURE_PEDIATRIC"));
        log.info("Matched LMS {} question pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_LMS_GERMLINE_PEX);

        //todo Osteo Somatic/CONSENT_ADDENDUM_PEDIATRIC
        //update OSTEO Somatic pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getOsteoSomaticPexIdToUpd();
        log.info("Matched OSTEO {} Somatic pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_OSTEO_SOMATIC_PEX);

        //update OSTEO Somatic Question pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getSomaticQuestionPexIdByStudyAnsQuestions(
                "CMI-OSTEO", "CONSENT_ADDENDUM_PEDIATRIC",
                Arrays.asList("SOMATIC_ASSENT_ADDENDUM", "ADDENDUM"));
        log.info("Matched OSTEO {} Somatic question pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_OSTEO_SOMATIC_PEX);

        //update LMS Somatic pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getLmsSomaticPexIdToUpd();
        log.info("Matched LMS {} Somatic pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_LMS_SOMATIC_PEX);

        //update LMS Somatic Question pex
        matchedExprIds = handle.attach(PecgsSomaticGermlinePexUpdate.SqlHelper.class).getSomaticQuestionPexIdByStudyAnsQuestions(
                "cmi-lms", "CONSENT_ADDENDUM_PEDIATRIC",
                Arrays.asList("SOMATIC_ASSENT_ADDENDUM", "ADDENDUM"));
        log.info("Matched LMS {} Somatic question pex expressions.. IDs: {}", matchedExprIds.size(), matchedExprIds);
        updatePexExpressions(handle, matchedExprIds, NEW_LMS_SOMATIC_PEX);

    }

    private void updatePexExpressions(Handle handle, List<Long> matchedExprIds, String newPexExpr) {
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
        int updatedPexCount = 0;
        for (Long expressionId : matchedExprIds) {
            String currentExpr = jdbiExpression.getExpressionById(expressionId);
            int udpCount = jdbiExpression.updateById(expressionId, newPexExpr);
            DBUtils.checkUpdate(1, udpCount);
            updatedPexCount++;
            log.info("Updated expressionId  {} with expr text \n{}. \nOld expr: \n{} ", expressionId, newPexExpr, currentExpr);
        }
        log.info("Updated {} pex expressions", updatedPexCount);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select e.expression_id from block b , block__expression bee, expression e "
                + "where bee.block_id = b.block_id "
                + "and e.expression_id = bee.expression_id and b.block_type_id != 5 "
                + "and e.expression_text like '%user.studies[\"CMI-OSTEO\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"]."
                + "questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()%"
                + "%operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && operator.studies[\"cmi-osteo\"].%'")
                //+ "forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7)\n"
                //+ " || (user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && user.studies[\"cmi-osteo\"].forms[\"PREQUAL\"]."
                //+ "questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7)%'")
        List<Long> getPexIdToUpd();

        @SqlQuery("select e.expression_id from block b , block__expression bee, expression e "
                + "where bee.block_id = b.block_id and b.block_type_id != 5 "
                + "and e.expression_id = bee.expression_id "
                + "and e.expression_text like '%user.studies[\"CMI-LMS\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"]."
                + "questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()%"
                + " && operator.studies[\"CMI-LMS\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7%'")
        List<Long> getLmsPexIdToUpd();

        @SqlQuery("select e.expression_id from block b , block__expression bee, expression e "
                + " where bee.block_id = b.block_id and b.block_type_id != 5 "
                + " and e.expression_id = bee.expression_id "
                + " and e.expression_text like "
                + "'!(operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"].answers.hasOption(\"CA\") ||\n" +
                " %operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_STATE\"].answers.hasOption(\"NY\")) &&\n" +
                " %operator.studies[\"cmi-lms\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7 &&\n" +
                " %user.studies[\"cmi-lms\"].forms[\"CONSENT_ASSENT\"].questions[\"CONSENT_ASSENT_TISSUE\"].answers.hasTrue()%'")
        List<Long> getLmsSomaticPexIdToUpd();

        @SqlQuery("select e.expression_id from block b , block__expression bee, expression e "
                + " where bee.block_id = b.block_id and b.block_type_id != 5 "
                + " and e.expression_id = bee.expression_id "
                + " and e.expression_text like "
                + "'%operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance()"
                + "%user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_COUNTRY\"].answers.hasOption(\"CA\")%'")
        List<Long> getOsteoSomaticPexIdToUpd();

        @SqlQuery(" select e.expression_id \n"
                + "    from question q, question_stable_code qsc, study_activity sa, umbrella_study s"
                + "    , block b, block__question bq, block_nesting bn, block__expression be, expression e\n"
                + "    where q.question_stable_code_id = qsc.question_stable_code_id\n"
                + "    and sa.study_activity_id = q.study_activity_id\n"
                + "    and s.umbrella_study_id = sa.study_id\n"
                + "    and bq.question_id = q.question_id\n"
                + "    and b.block_id = bq.block_id\n"
                + "    and bn.nested_block_id = b.block_id\n"
                + "    and be.block_id = bn.parent_block_id"
                + "    and e.expression_id = be.expression_id\n"
                + "    and s.guid = :studyGuid\n"
                + "    and sa.study_activity_code = :activityCode "
                + "    and qsc.stable_id in (<stableIds>) ;\n")
        List<Long> getSomaticQuestionPexIdByStudyAnsQuestions(@Bind("studyGuid") String studyGuid, @Bind("activityCode") String activityCode,
                                                              @BindList("stableIds") List<String> stableIds);

        @SqlQuery(" select e.expression_id \n"
                + "    from question q, question_stable_code qsc, study_activity sa, umbrella_study s"
                + "    , block b, block__question bq, block__expression be, expression e\n"
                + "    where q.question_stable_code_id = qsc.question_stable_code_id\n"
                + "    and sa.study_activity_id = q.study_activity_id\n"
                + "    and s.umbrella_study_id = sa.study_id\n"
                + "    and bq.question_id = q.question_id\n"
                + "    and b.block_id = bq.block_id\n"
                + "    and be.block_id = b.block_id\n"
                + "    and e.expression_id = be.expression_id\n"
                + "    and s.guid = :studyGuid\n"
                + "    and sa.study_activity_code = :activityCode "
                + "    and qsc.stable_id in (<stableIds>) ;\n")
        List<Long> getQuestionPexIdByStudyAnsQuestions(@Bind("studyGuid") String studyGuid, @Bind("activityCode") String activityCode,
                                                       @BindList("stableIds") List<String> stableIds);

    }

}

