package org.broadinstitute.dsm.util;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PDFAudit {

    private static final Logger logger = LoggerFactory.getLogger(PDFAudit.class);

    public void checkAndSavePDF() {
        // if there are newer kits save pdfs
        Long bookmark = DBUtil.getBookmark(DBConstants.PDF_AUDIT_KIT);
        if (bookmark != null && bookmark != -1) {
            List<KitRequestShipping> kitRequests = KitRequestShipping.getKitRequestsAfterBookmark(bookmark);
            if (kitRequests != null && !kitRequests.isEmpty()) {
                logger.info("Uploading consent and release pdfs for kit requests");
                long newAuditKit = bookmark;
                try {
                    for (KitRequestShipping request : kitRequests) {
                        // ddp with 'standard' consent and release pdfs
                        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(request.getRealm(), DBConstants.PDF_DOWNLOAD_CONSENT); //the role for release will get checked in makePDF
                        if (instance != null && StringUtils.isNotBlank(instance.getBaseUrl()) && instance.isHasRole()) {
                            DDPRequestUtil.makeStandardPDF(instance, request.getParticipantId(), request.getCreatedBy(), request.getKitType());
                        }
                        else {
                            // ddp without 'standard' consent and release pdfs
                            instance = DDPInstance.getDDPInstanceWithRole(request.getRealm(), DBConstants.PDF_DOWNLOAD); //the role for release will get checked in makePDF
                            if (instance != null && StringUtils.isNotBlank(instance.getBaseUrl()) && StringUtils.isNotBlank(instance.getParticipantIndexES())
                                    && instance.isHasRole()) {
                                DDPRequestUtil.makeNonStandardPDF(instance, request.getParticipantId(), request.getCreatedBy(), request.getKitType());
                            }
                        }
                        newAuditKit = Math.max(newAuditKit, Integer.parseInt(request.getDsmKitRequestId()));
                    }
                }
                catch (Exception e) {
                    logger.error("PDF audit failed ", e);
                }
                DBUtil.updateBookmark(newAuditKit, DBConstants.PDF_AUDIT_KIT);
            }
        }
    }
}
