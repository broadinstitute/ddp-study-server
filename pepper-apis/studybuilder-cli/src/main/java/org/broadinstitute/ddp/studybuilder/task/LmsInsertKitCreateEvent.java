package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.jdbi.v3.core.Handle;

import java.util.List;

@Slf4j
public class LmsInsertKitCreateEvent extends InsertStudyEvents {
    public LmsInsertKitCreateEvent() {
        super("cmi-lms", "patches/lms-blood-kit-event.conf");
    }

    @Override
    public void run(final Handle handle) {
        removeExistingBloodKitConfig(handle);
        super.run(handle);
    }

    private void removeExistingBloodKitConfig(final Handle handle) {
        KitConfigurationDao kitConfigDao = handle.attach(KitConfigurationDao.class);
        List<KitConfigurationDto> kitConfigs = kitConfigDao.getKitConfigurationDtosByStudyId(handle.attach(JdbiUmbrellaStudy.class)
                        .findByStudyGuid(studyGuid).getId());

        long bloodKitTypeid = handle.attach(KitTypeDao.class).getBloodKitType().getId();
        KitConfigurationDto kitConfigDto = kitConfigs.stream().filter(dto -> dto.getKitTypeId()
                == bloodKitTypeid).findFirst().get();

        KitConfiguration kitConfig = kitConfigDao.getKitConfigurationForDto(kitConfigDto);
        kitConfig.getRules().stream().forEach(kitRule -> kitConfigDao.getJdbiKitRules().deleteRuleFromConfiguration(
                kitConfig.getId(), kitRule.getId()));

        DBUtils.checkDelete(1, kitConfigDao.deleteConfiguration(kitConfigDto.getId()));
        log.info("Successfully deleted {} Blood Kit Config {} of {}.", 1, kitConfigDto.getId(), studyGuid);
    }

}
