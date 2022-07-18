package org.broadinstitute.dsm.model.patch;

import java.util.HashMap;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.InvitaeReportDao;
import org.broadinstitute.dsm.model.NameValue;

@Slf4j
public class InvitaePatch extends BasePatch {
    public static final String INVITAE_REPORT_ID = "invitaeReportId";

    static {
        NULL_KEY = new HashMap<>();
        NULL_KEY.put(NAME_VALUE, null);
    }

    private String participantId;
    private String invitaeReportId;

    {
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
    }

    public InvitaePatch(Patch patch) {
        super(patch);
    }

    @Override
    public Object doPatch() {
        return isNameValuePairs() ? patchNameValuePairs() : patchNameValuePair();
    }

    private void prepare() {
        participantId = patch.getParentId();
        if (participantId != null && StringUtils.isBlank(patch.getId())) {
            invitaeReportId = InvitaeReportDao.createNewInvitaeReport(participantId, patch.getUser());
        } else {
            invitaeReportId = patch.getId();
        }
        resultMap.put(INVITAE_REPORT_ID, invitaeReportId);
    }

    @Override
    protected Object patchNameValuePairs() {
        prepare();
        if (participantId == null) {
            logger.error("No Participant found for Invitae report ");
            return NULL_KEY;
        }
        processMultipleNameValues();
        return resultMap;
    }

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeSingleNameValue = processSingleNameValue();
        return maybeSingleNameValue.orElse(resultMap);
    }

    @Override
    Object handleSingleNameValue() {
        if (Patch.patch(invitaeReportId, patch.getUser(), patch.getNameValue(), dbElement)) {
            exportToESWithId(invitaeReportId, patch.getNameValue());
        }
        resultMap.put(NAME_VALUE, GSON.toJson(nameValues));
        return resultMap;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        Patch.patch(invitaeReportId, patch.getUser(), nameValue, dbElement);
        exportToESWithId(invitaeReportId, nameValue);
        return Optional.empty();
    }
}
