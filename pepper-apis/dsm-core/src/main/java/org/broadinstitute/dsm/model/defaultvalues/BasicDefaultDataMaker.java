package org.broadinstitute.dsm.model.defaultvalues;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.settings.field.FieldSettings;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BasicDefaultDataMaker implements Defaultable {

    protected static final Logger logger = LoggerFactory.getLogger(RgpAutomaticProbandDataCreator.class);

    protected final FieldSettings fieldSettings = new FieldSettings();
    protected final BookmarkDao bookmarkDao = new BookmarkDao();
    protected final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    protected final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    protected DDPInstance instance;
    protected ElasticSearchParticipantDto elasticSearchParticipantDto;



    protected abstract boolean setDefaultData();

    @Override
    public boolean generateDefaults(String studyGuid, String participantId) {
        String esParticipantIndex = ddpInstanceDao.getEsParticipantIndexByStudyGuid(studyGuid)
                .orElse(StringUtils.EMPTY);
        Optional<ElasticSearchParticipantDto> maybeParticipantESDataByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esParticipantIndex, participantId);
        if (maybeParticipantESDataByParticipantId.isEmpty()) {
            logger.warn("Could not create proband/self data, participant ES data is null");
            return false;
        }
        instance = DDPInstance.getDDPInstance(studyGuid);
        elasticSearchParticipantDto = maybeParticipantESDataByParticipantId.get();
        return setDefaultData();
    }
}
