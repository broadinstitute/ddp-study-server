package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiPicklistGroupedOption;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.broadinstitute.ddp.db.dao.PicklistQuestionDao.DISPLAY_ORDER_GAP;

@Slf4j
public class PancanNewGastroCancers implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/pedhcc-new-gastro-cancers.conf";

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

        log.info("Running task: PancanNewGastroCancers to add 2 new Gastro cancers for pedHCC");

        String groupSid = dataCfg.getString("groupSid");
        for (String questionSid : dataCfg.getStringList("questions")) {
            for (Config option : dataCfg.getConfigList("options")) {
                PicklistOptionDef optionDef = gson.fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class);
                insertOptionToGroup(handle, questionSid, groupSid, optionDef);
            }
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

        log.info("Successfully added option {} with order {} to group {} for question {}",
                optionDef.getStableId(), order, groupSid, questionSid);
    }

    private QuestionDto findLatestQuestionDto(Handle handle, String questionSid) {
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), questionSid)
                .orElseThrow(() -> new DDPException("Couldnt find question with stable code " + questionSid));
    }
}
