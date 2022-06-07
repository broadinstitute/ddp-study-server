package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.concurrent.BlockingQueue;

import org.broadinstitute.dsm.export.ParticipantExcelGenerator;


public class ParticipantConsumer {
    private final BlockingQueue<ExcelRow> participantsQueue;
    private final ParticipantExcelGenerator generator = new ParticipantExcelGenerator();

    public ParticipantConsumer(BlockingQueue<ExcelRow> participantsQueue) {
        this.participantsQueue = participantsQueue;
    }

    public ParticipantExcelGenerator get() throws InterruptedException {
        while (true) {
            ExcelRow data = participantsQueue.take();
            if (data.getRowData().isEmpty()) {
                break;
            }
            generator.appendData(data);
        }
        return generator;
    }
}
