package org.broadinstitute.ddp.service;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.ConsentElectionDao;
import org.broadinstitute.ddp.db.StudyActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentService {

    private static final Logger LOG = LoggerFactory.getLogger(ConsentService.class);

    private final PexInterpreter interpreter;
    private final StudyActivityDao studyActDao;
    private final ConsentElectionDao consentElectionDao;

    public ConsentService(PexInterpreter interpreter, StudyActivityDao studyActDao,
                          ConsentElectionDao consentElectionDao) {
        this.interpreter = interpreter;
        this.studyActDao = studyActDao;
        this.consentElectionDao = consentElectionDao;
    }

    public static ConsentService createInstance() {
        return new ConsentService(new TreeWalkInterpreter(), new StudyActivityDao(), new ConsentElectionDao());
    }

    /**
     * Get all consent summaries for user and study, along with elections. If an activity instance
     * is available for user, consent status and election selected status will be evaluated.
     *
     * @param handle    the jdbi handle
     * @param userGuid  the user guid
     * @param studyGuid the study guid
     * @return list of summaries, or empty
     */
    public List<ConsentSummary> getAllConsentSummariesByUserGuid(Handle handle, String userGuid, String studyGuid) {
        List<ConsentSummary> summaries = studyActDao.getAllConsentSummaries(handle, userGuid, studyGuid);
        Optional<Long> studyIdOpt = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
        for (ConsentSummary summary : summaries) {
            resolveStatusAndElections(handle, userGuid, summary, studyIdOpt.get());
        }
        return summaries;
    }

    /**
     * Get specified consent activity, along with elections. If an activity instance is available
     * for user, consent status and election selected status will be evaluated.
     *
     * @param handle       the jdbi handle
     * @param userGuid     the user guid
     * @param studyGuid    the study guid
     * @param consentActivityCode the consent activity code
     * @return the latest summary, if found
     */
    public Optional<ConsentSummary> getLatestConsentSummary(Handle handle, String userGuid,
                                                      String studyGuid, String consentActivityCode) {
        Optional<Long> studyIdOpt = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
        Optional<ConsentSummary> optSummary = studyActDao.getLatestConsentSummary(
                handle, userGuid, studyGuid, consentActivityCode
        );
        optSummary.ifPresent(summary -> resolveStatusAndElections(handle, userGuid, summary, studyIdOpt.get()));
        return optSummary;
    }

    private void resolveStatusAndElections(Handle handle, String userGuid, ConsentSummary summary, long studyId) {
        if (StringUtils.isNotBlank(summary.getInstanceGuid()) && StringUtils.isBlank(summary.getConsentedExpr())) {
            throw new DDPException("Activity instance found for consent activity "
                    + summary.getActivityCode() + " but no consented expression found. "
                    + "Perhaps activity instance creation timestamp falls outside of valid consent revisions.");
        }
        if (StringUtils.isNotBlank(summary.getInstanceGuid())) {
            try {
                summary.setConsented(interpreter.eval(summary.getConsentedExpr(), handle, userGuid, summary.getInstanceGuid()));
            } catch (PexException e) {
                throw new DDPException("Error evaluating pex expression `" + summary.getConsentedExpr()
                        + "` for consent " + summary.getActivityCode(), e);
            }

            List<ConsentElection> elections = consentElectionDao.getElections(handle,
                    summary.getActivityCode(), summary.getInstanceGuid(), studyId);
            for (ConsentElection election : elections) {
                try {
                    election.setSelected(interpreter.eval(election.getSelectedExpr(),
                            handle, userGuid, summary.getInstanceGuid()));
                } catch (PexException e) {
                    throw new DDPException("Error evaluating pex expression `" + election.getSelectedExpr()
                            + "` for election " + election.getStableId(), e);
                }
            }
            summary.setElections(elections);
        } else {
            LOG.info("Fetching latest elections for consent {} without evaluating expressions",
                    summary.getActivityCode());
            summary.setElections(consentElectionDao.getLatestElections(handle, summary.getActivityCode(), studyId));
        }
    }
}
