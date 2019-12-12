package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackfillCompositeOrientations implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillCompositeOrientations.class);
    private static final String DATA_FILE = "patches/backfill-composite-orientations.conf";

    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Study is missing data file for backfills: " + file);
        }

        dataCfg = ConfigFactory.parseFile(file);
        cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        SqlHelper helper = handle.attach(SqlHelper.class);
        List<? extends Config> questions = dataCfg.getConfigList("questions");
        for (Config question : questions) {
            String stableId = question.getString("stableId");
            OrientationType orientation = question.getEnum(OrientationType.class, "orientation");
            helper.backfillCompositeOrientation(studyDto.getId(), stableId, orientation);
            LOG.info("backfilled question {} with orientation {}", stableId, orientation);
        }
        LOG.info("backfilled orientation for {} composite questions", questions.size());
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update composite_question as cq"
                + "   join question as q on q.question_id = cq.question_id"
                + "   join question_stable_code as qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + "    set cq.child_orientation_type_id = ("
                + "        select orientation_type_id from orientation_type where orientation_type_code = :type)"
                + "  where qsc.umbrella_study_id = :studyId"
                + "    and qsc.stable_id = :stableId")
        int _updateCompositeOrientationType(@Bind("studyId") long studyId,
                                            @Bind("stableId") String stableId,
                                            @Bind("type") OrientationType type);

        default void backfillCompositeOrientation(long studyId, String stableId, OrientationType type) {
            int numUpdated = _updateCompositeOrientationType(studyId, stableId, type);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update orientation for 1 row with studyId="
                        + studyId + " stableId=" + stableId + " but updated " + numUpdated);
            }
        }
    }
}
