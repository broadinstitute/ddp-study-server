package org.broadinstitute.dsm.model.stool.upload;

import java.util.List;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDao;
import org.broadinstitute.dsm.db.dao.stoolupload.StoolUploadDto;
import org.broadinstitute.dsm.files.parser.AbstractRecordsParser;
import org.broadinstitute.dsm.files.parser.stool.TSVStoolUploadRecordsParser;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoolUploadService {

    private static final Logger logger = LoggerFactory.getLogger(StoolUploadService.class);

    private final StoolUploadServicePayload stoolUploadServicePayload;
    private final StoolUploadDao stoolUploadDao = new StoolUploadDao();

    private StoolUploadService(StoolUploadServicePayload stoolUploadServicePayload) {
        this.stoolUploadServicePayload = stoolUploadServicePayload;
    }

    public static StoolUploadService spawn(StoolUploadServicePayload stoolUploadServicePayload) {
        return new StoolUploadService(stoolUploadServicePayload);
    }

    public void serve() {
        AbstractRecordsParser<StoolUploadDto> tsvRecordsParser =
                new TSVStoolUploadRecordsParser(stoolUploadServicePayload.getRequestBody());
        List<StoolUploadDto> stoolUploadObjects = tsvRecordsParser.parseToObjects();
        stoolUploadObjects.forEach(this::updateKitAndThenSendNotification);
    }

    private void updateKitAndThenSendNotification(StoolUploadDto stoolUploadDto) {
        boolean updated = stoolUploadDao.updateKit(stoolUploadDto);
        if (updated) {
            KitDDPNotification kitDDPNotification = KitDDPNotification.getKitDDPNotification(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL),
                    stoolUploadDto.getMfBarcode(), 1);
            if (kitDDPNotification != null) {
                logger.info("Triggering DDP to send emails");
                TransactionWrapper.inTransaction(conn -> {
                    EventUtil.triggerDDP(conn, kitDDPNotification);
                    return null;
                });
                stoolUploadServicePayload.getResponse().status(200);
            } else {
                logger.warn(String.format("No notification was found for barcode %s", stoolUploadDto.getMfBarcode()));
            }
        } else {
            stoolUploadServicePayload.getResponse().status(500);
        }
    }
}

