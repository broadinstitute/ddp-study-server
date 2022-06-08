package org.broadinstitute.dsm.model.elastic.export.excel;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import spark.QueryParamsMap;

public class ParticipantProducer {
    private final Filterable filter;
    private final QueryParamsMap queryParamsMap;
    private final ParticipantRecordData rowData;
    private final BlockingQueue<ExcelRow> participantsQueue;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public ParticipantProducer(Filterable filter, QueryParamsMap queryParamsMap, Map<Alias, List<Filter>> columnAliasEsPathMap,
                               BlockingQueue<ExcelRow> participantsQueue) {
        this.filter = filter;
        this.queryParamsMap = queryParamsMap;
        this.participantsQueue = participantsQueue;
        this.rowData = new ParticipantRecordData(columnAliasEsPathMap);
    }

    public void start() throws InterruptedException {
        executorService.submit(new ParticipantDataProcessor(true));
        executorService.submit(new ParticipantDataProcessor(false));
        executorService.shutdown();
    }

    private class ParticipantDataProcessor implements Runnable {
        private final boolean isCount;

        private ParticipantDataProcessor(boolean isCount) {
            this.isCount = isCount;
        }

        @Override
        public void run() {
            int currentFrom = DEFAULT_FROM;
            int currentTo = MAX_RESULT_SIZE;
            while (true) {
                filter.setFrom(currentFrom);
                filter.setTo(currentTo);
                ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filter.filter(queryParamsMap);
                List<ExcelRow> participantRows = rowData.processData(filteredList, isCount);
                if (!isCount) {
                    participantsQueue.addAll(participantRows);
                }
                if (filteredList.getTotalCount() < currentTo) {
                    break;
                }
                currentFrom = currentTo;
                currentTo += MAX_RESULT_SIZE;
            }
        }

    }
}
