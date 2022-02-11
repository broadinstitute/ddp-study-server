package org.broadinstitute.dsm.model.PDF;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.files.RequestPDFProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TissueCoverPDF{
    Logger logger = LoggerFactory.getLogger(TissueCoverPDF.class);
    private DownloadPDF originalDownloadPDF;

    public TissueCoverPDF(@NonNull DownloadPDF downloadPDF){
        this.originalDownloadPDF = downloadPDF;
    }

    public Map<String, Object> getValuesForTissueCover(DDPInstance ddpInstance, UserDto user) {

        logger.info("Generating request pdf for onc history ids {}", StringUtils.join(originalDownloadPDF.oncHistoryIDs, ","));
        Map<String, Object> valueMap = new HashMap<>();
        String today = new SimpleDateFormat("MM/dd/yyyy").format(new Date());
        valueMap.put(RequestPDFProcessor.FIELD_DATE, today);
        valueMap.put(RequestPDFProcessor.FIELD_DATE_2, "(" + today + ")");
        originalDownloadPDF.addDDPParticipantDataToValueMap(ddpInstance,valueMap, false,  originalDownloadPDF.getDdpParticipantId());
        int counter = 0;
        if (originalDownloadPDF.oncHistoryIDs != null) {
            for (int i = 0; i < originalDownloadPDF.oncHistoryIDs.size(); i++) {
                OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(originalDownloadPDF.oncHistoryIDs.get(i), ddpInstance.getName());
                // facility information is the same in all of the requests so only need to be set ones!
                if (i == 0) {
                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_INSTITUTION_NAME, oncHistoryDetail.getFacility());
                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_PHONE, oncHistoryDetail.getPhone());
                    valueMap.put(RequestPDFProcessor.FIELD_CONFIRMED_FAX, oncHistoryDetail.getFax());
                }
                valueMap.put(RequestPDFProcessor.FIELD_DATE_PX + i, oncHistoryDetail.getDatePx());
                valueMap.put(RequestPDFProcessor.FIELD_TYPE_LOCATION + i, oncHistoryDetail.getTypePx());
                valueMap.put(RequestPDFProcessor.FIELD_ACCESSION_NUMBER + i, oncHistoryDetail.getAccessionNumber());
                counter = i;
            }
        }
        valueMap.put(RequestPDFProcessor.BLOCK_COUNTER, counter + 1);

        valueMap.put(RequestPDFProcessor.USER_NAME, user.getName().get());
        user.getPhoneNumber().ifPresent(phone -> valueMap.put(RequestPDFProcessor.USER_PHONE, phone));
        return valueMap;
    }
    public byte[] getTissueCoverPDF(DDPInstance ddpInstance, UserDto user){
        RequestPDFProcessor processor = new RequestPDFProcessor(ddpInstance.getName());
        return originalDownloadPDF.generatePDFFromValues(this.getValuesForTissueCover( ddpInstance, user), ddpInstance, processor);
    }
}
