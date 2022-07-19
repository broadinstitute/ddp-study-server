package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.InvitaeReportDao;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class InvitaeReportMigrator extends BaseCollectionMigrator {

    public InvitaeReportDao invitaeReportDao;

    public InvitaeReportMigrator(String index, String realm, InvitaeReportDao invitaeReportDao) {
        super(index, realm, ESObjectConstants.INVITAE_REPORT);
        this.invitaeReportDao = invitaeReportDao;
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return invitaeReportDao.getInvitaeReportByStudy(realm);
    }

    @Override
    public String getPropertyName() {
        return null;
    }
}
