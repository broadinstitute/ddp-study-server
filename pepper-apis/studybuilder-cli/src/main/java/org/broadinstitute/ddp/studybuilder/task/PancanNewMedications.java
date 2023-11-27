package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiTextQuestionSuggestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.broadinstitute.ddp.db.dao.PicklistQuestionDao.DISPLAY_ORDER_GAP;

@Slf4j
public class PancanNewMedications implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/pancan-new-medications.conf";

    private Config studyCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        String stableId = dataCfg.getString("stableId");
        SqlHelper sqlHelper = handle.attach(SqlHelper.class);
        long questionId = sqlHelper.findQuestionIdByStableIdForStudy(stableId, studyDto.getId());
        List<String> suggestions = new ArrayList<>(sqlHelper.findAllSuggestions(questionId));
        sqlHelper.deleteCurrentSuggestionsForQuestion(questionId);
        JdbiTextQuestionSuggestion jdbiSuggestion = handle.attach(JdbiTextQuestionSuggestion.class);
        log.info("suggestions size before: {}", suggestions.size());
        suggestions.addAll(dataCfg.getStringList("medications"));
        log.info("new meds {}", dataCfg.getStringList("medications"));
        Collections.sort(suggestions);
        int[] ids = jdbiSuggestion.insert(questionId, suggestions,
                Stream.iterate(0, j -> j + DISPLAY_ORDER_GAP).iterator());
        if (ids.length != suggestions.size()) {
            throw new DaoException("Error inserting pancan medication suggestions:" + stableId);
        }
        log.info("suggestions size after: {}", suggestions.size());
        log.info("Successfully added new medications: " + stableId);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select question_id from question q"
                + "         join question_stable_code qsc on q.question_stable_code_id = qsc.question_stable_code_id"
                + "  where qsc.stable_id = :stableId"
                + "  and qsc.umbrella_study_id = :studyId;")
        int findQuestionIdByStableIdForStudy(@Bind("stableId") String stableId, @Bind("studyId") long studyId);

        @SqlQuery("select suggestion from text_question_suggestion where text_question_id=:questionId")
        List<String> findAllSuggestions(@Bind("questionId") long questionId);

        @SqlUpdate("delete from text_question_suggestion where text_question_id=:questionId")
        void deleteCurrentSuggestionsForQuestion(long questionId);
    }
}
