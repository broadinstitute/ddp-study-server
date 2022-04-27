package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiPicklistGroupedOption;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import static org.broadinstitute.ddp.db.dao.PicklistQuestionDao.DISPLAY_ORDER_GAP;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Task to make additional edits as part of the "Brain Tumor Project" rename.
 *
 * <p>This should be ran right after the BrainRename task. This assumes that activities will have a new version from
 * the BrainRename task, so it will make edits using that as the latest version.
 */
@Slf4j
public class PancanNewCancer implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/sarcomas-new-cancer.conf";

    private Config studyCfg;
    private Config dataCfg;
    private Gson gson;

    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        log.info("Editing PANCAN study... ");

        String groupSid = dataCfg.getString("groupSid");

        for (String questionSid : dataCfg.getStringList("questions")) {
            PicklistOptionDef option = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("option")), PicklistOptionDef.class);
            insertOptionToGroup(handle, questionSid, groupSid, option);
        }
    }

    private void insertOptionToGroup(Handle handle, String questionSid, String groupSid, PicklistOptionDef optionDef) {

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiPicklistGroupedOption picklistGroupedOption = handle.attach(JdbiPicklistGroupedOption.class);

        QuestionDto question = findLatestQuestionDto(handle, questionSid);


        var groupAndOptionDtos = plQuestionDao.findOrderedGroupAndOptionDtos(question.getId(), question.getRevisionStart());

        PicklistGroupDto groupDto = groupAndOptionDtos.getGroups()
                .stream()
                .filter(g -> g.getStableId().equals(groupSid)).findFirst()
                .orElseThrow(() ->
                        new DDPException(String.format("Couldn't find the group %s for the question %s", groupSid, question.getId())));

        var list = groupAndOptionDtos.getGroupIdToOptions().get(groupDto.getId());
        var lastDto = list.get(list.size() - 1);
        var order = lastDto.getDisplayOrder() + DISPLAY_ORDER_GAP;

        long optionId = plQuestionDao.insertOption(question.getId(), optionDef, order, groupDto.getRevisionId());

        long[] ids = picklistGroupedOption.bulkInsert(groupDto.getId(), List.of(optionId));
        if (ids.length != 1) {
            throw new DaoException("Could not add option to group " + groupSid);
        }
        log.info("Successfully added option to group {} for question {}", groupSid, questionSid);
    }

    private QuestionDto findLatestQuestionDto(Handle handle, String questionSid) {
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), questionSid)
                .orElseThrow(() -> new DDPException("Couldnt find question with stable code " + questionSid));
    }
}
