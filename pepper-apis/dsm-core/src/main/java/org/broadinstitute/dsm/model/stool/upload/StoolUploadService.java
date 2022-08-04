package org.broadinstitute.dsm.model.stool.upload;

import java.util.List;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDao;
import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadObject;
import org.broadinstitute.dsm.files.parser.TSVRecordsParser;
import org.broadinstitute.dsm.files.parser.stool.TSVStoolUploadRecordsParser;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoolUploadService {

    private static final Logger logger = LoggerFactory.getLogger(StoolUploadService.class);

    private final StoolUploadServicePayload stoolUploadCoordinatorPayload;
    private final StoolUploadDao stoolUploadDao = new StoolUploadDao();

    public StoolUploadService(StoolUploadServicePayload stoolUploadCoordinatorPayload) {
        this.stoolUploadCoordinatorPayload = stoolUploadCoordinatorPayload;
    }

    public void serve() {
        TSVRecordsParser<StoolUploadObject> tsvRecordsParser =
                new TSVStoolUploadRecordsParser(stoolUploadCoordinatorPayload.getRequestBody());
        List<StoolUploadObject> stoolUploadObjects = tsvRecordsParser.parseToObjects();
        stoolUploadObjects.forEach(this::updateKitAndThenSendNotification);
    }

    private void updateKitAndThenSendNotification(StoolUploadObject stoolUploadObject) {
        boolean updated = stoolUploadDao.updateKit(stoolUploadObject);
        if (updated) {
            KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL),
                    stoolUploadObject.getMfBarcode(), 1);
            if (kitDDPNotification != null) {
                logger.info("Triggering DDP to send emails");
                TransactionWrapper.inTransaction(conn -> {
                    EventUtil.triggerDDP(conn, kitDDPNotification);
                    return null;
                });
                stoolUploadCoordinatorPayload.getResponse().status(200);
            } else {
                logger.warn(String.format("No notification was found for barcode %s", stoolUploadObject.getMfBarcode()));
            }
        } else {
            stoolUploadCoordinatorPayload.getResponse().status(500);
        }
    }
}

