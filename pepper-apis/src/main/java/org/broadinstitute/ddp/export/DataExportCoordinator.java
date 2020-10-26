package org.broadinstitute.ddp.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.study.Participant;
import org.jdbi.v3.core.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataExportCoordinator {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int READER_BUFFER_SIZE_IN_BYTES = 10 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(DataExportCoordinator.class);

    private DataExporter exporter;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private Set<ElasticSearchIndexType> indices = new HashSet<>();
    private Bucket csvBucket;

    public DataExportCoordinator(DataExporter exporter) {
        this.exporter = exporter;
    }

    public DataExportCoordinator withBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public DataExportCoordinator includeIndex(ElasticSearchIndexType index) {
        this.indices.add(index);
        return this;
    }

    public DataExportCoordinator includeCsv(Bucket csvBucket) {
        this.csvBucket = csvBucket;
        return this;
    }

    public boolean export(StudyDto studyDto) {
        boolean success = true;
        if (indices.isEmpty() && csvBucket == null) {
            return true;
        }

        Set<ElasticSearchIndexType> todoIndices = new HashSet<>(indices);
        List<ActivityExtract> activities = withAPIsTxn(handle -> {
            var index = ElasticSearchIndexType.ACTIVITY_DEFINITION;
            if (todoIndices.remove(index)) {
                LOG.info("Running {} elasticsearch export for study {}", index, studyDto.getGuid());
                long start = Instant.now().toEpochMilli();
                var extracts = exporter.exportActivityDefinitionsToElasticsearch(handle, studyDto);
                long elapsed = Instant.now().toEpochMilli() - start;
                LOG.info("Finished {} elasticsearch export for study {} in {}s", index, studyDto.getGuid(), elapsed / 1000);
                return extracts;
            } else {
                return exporter.extractActivities(handle, studyDto);
            }
        });

        if (!indices.isEmpty()) {
            success = runElasticExports(studyDto, activities, todoIndices);
        }

        if (csvBucket != null) {
            boolean runSuccess = runCsvExports(studyDto, activities);
            success = success && runSuccess;
        }

        return success;
    }

    private boolean runElasticExports(StudyDto studyDto, List<ActivityExtract> activities, Set<ElasticSearchIndexType> todoIndices) {
        boolean success = true;
        if (!todoIndices.isEmpty()) {
            try {
                LOG.info("Running paginated participant elasticsearch export for study {}", studyDto.getGuid());
                long start = Instant.now().toEpochMilli();
                success = paginatedExportToIndices(studyDto, activities, todoIndices);
                long elapsed = Instant.now().toEpochMilli() - start;
                LOG.info("Finished paginated participant elasticsearch export for study {} in {}s", studyDto.getGuid(), elapsed / 1000);
            } catch (Exception e) {
                LOG.error("Error while running paginated participant elasticsearch for study {}, continuing", studyDto.getGuid(), e);
                success = false;
            }
        }
        return success;
    }

    private boolean paginatedExportToIndices(StudyDto studyDto, List<ActivityExtract> activities, Set<ElasticSearchIndexType> indices) {
        String studyGuid = studyDto.getGuid();
        boolean success = true;
        int fetched = 0;
        while (true) {
            int offset = fetched;
            Set<Long> batch = withAPIsTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                    .findUserIdsByStudyIdAndLimit(studyDto.getId(), offset, batchSize));
            int fetchedSize = batch.size();
            if (fetchedSize == 0) {
                break;
            }

            boolean runSuccess = withAPIsTxn(handle -> {
                boolean batchSuccess = true;
                List<Participant> participants = exporter.extractParticipantDataSetByIds(handle, studyDto, batch);
                LOG.info("Extracted {} participants for study {}", participants.size(), studyDto.getGuid());
                if (indices.contains(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED)) {
                    try {
                        exporter.exportToElasticsearch(handle, studyDto, activities, participants, true);
                    } catch (Exception e) {
                        LOG.error("Error while running {} elasticsearch export for study {}, continuing",
                                ElasticSearchIndexType.PARTICIPANTS_STRUCTURED, studyGuid, e);
                        batchSuccess = false;
                    }
                }
                if (indices.contains(ElasticSearchIndexType.PARTICIPANTS)) {
                    try {
                        exporter.exportToElasticsearch(handle, studyDto, activities, participants, false);
                    } catch (Exception e) {
                        LOG.error("Error while running {} elasticsearch export for study {}, continuing",
                                ElasticSearchIndexType.PARTICIPANTS, studyGuid, e);
                        batchSuccess = false;
                    }
                }
                if (indices.contains(ElasticSearchIndexType.USERS)) {
                    try {
                        exporter.exportUsersToElasticsearch(handle, studyDto, batch);
                    } catch (Exception e) {
                        LOG.error("Error while running {} elasticsearch export for study {}, continuing",
                                ElasticSearchIndexType.USERS, studyGuid, e);
                        batchSuccess = false;
                    }
                }
                return batchSuccess;
            });
            success = success && runSuccess;

            fetched += fetchedSize;
        }
        LOG.info("Processed {} participants for study {}", fetched, studyDto.getGuid());
        return success;
    }

    private boolean runCsvExports(StudyDto studyDto, List<ActivityExtract> activities) {
        String studyGuid = studyDto.getGuid();
        try {
            LOG.info("Running csv export for study {}", studyGuid);
            long start = Instant.now().toEpochMilli();
            var iterator = new PaginatedParticipantIterator(studyDto, batchSize);
            exportStudyToGoogleBucket(studyDto, exporter, csvBucket, activities, iterator);
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Finished csv export for study {} in {}s", studyGuid, elapsed / 1000);
            return true;
        } catch (Exception e) {
            LOG.error("Error while running csv export for study {}, continuing", studyGuid, e);
            return false;
        }
    }

    boolean exportStudyToGoogleBucket(StudyDto studyDto, DataExporter exporter, Bucket bucket,
                                      List<ActivityExtract> activities,
                                      Iterator<Participant> participants) {
        try (
                PipedOutputStream outputStream = new PipedOutputStream();
                Writer csvWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                PipedInputStream csvInputStream = new PipedInputStream(outputStream, READER_BUFFER_SIZE_IN_BYTES);
        ) {
            // Running the DataExporter in separate thread
            Runnable csvExportRunnable = buildExportToCsvRunnable(studyDto, exporter, csvWriter, activities, participants);
            Thread csvExportThread = new Thread(csvExportRunnable);
            csvExportThread.start();

            String fileName = buildExportBlobFilename(studyDto);

            // Google writing happens on this thread
            return saveToGoogleBucket(csvInputStream, fileName, studyDto.getGuid(), bucket);
        } catch (IOException e) {
            throw new DDPException(e);
        }
    }

    Runnable buildExportToCsvRunnable(StudyDto studyDto, DataExporter exporter, Writer csvOutputWriter,
                                      List<ActivityExtract> activities,
                                      Iterator<Participant> participants) {
        return () -> {
            try {
                int total = exporter.exportDataSetAsCsv(studyDto, activities, participants, csvOutputWriter);
                LOG.info("Written {} participants to csv export for study {}", total, studyDto.getGuid());
                // closing here is important! Can't wait until the try block calls close
                csvOutputWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    boolean saveToGoogleBucket(InputStream csvInputStream, String fileName, String studyGuid, Bucket bucket) {
        Blob blob = bucket.create(fileName, csvInputStream, "text/csv");
        LOG.info("Uploaded file {} to bucket {} for study {}", blob.getName(), bucket.getName(), studyGuid);
        return true;
    }

    private String buildExportBlobFilename(StudyDto study) {
        return String.format("%s/%s", study.getName(), DataExporter.makeExportCSVFilename(study.getGuid(), Instant.now()));
    }

    <R, X extends Exception> R withAPIsTxn(HandleCallback<R, X> callback) throws X {
        return TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, callback);
    }

    private class PaginatedParticipantIterator implements Iterator<Participant> {

        private StudyDto studyDto;
        private int batchSize;
        private int fetched;
        private boolean exhausted;
        private ArrayDeque<Participant> currentBatch;

        public PaginatedParticipantIterator(StudyDto studyDto, int batchSize) {
            this.studyDto = studyDto;
            this.batchSize = batchSize;
            this.fetched = 0;
            this.exhausted = false;
            this.currentBatch = null;
        }

        @Override
        public boolean hasNext() {
            if (exhausted) {
                return false;
            }

            if (currentBatch == null) {
                int offset = fetched;
                currentBatch = withAPIsTxn(handle -> {
                    Set<Long> userIds = handle.attach(JdbiUserStudyEnrollment.class)
                            .findUserIdsByStudyIdAndLimit(studyDto.getId(), offset, batchSize);
                    List<Participant> extract = exporter.extractParticipantDataSetByIds(handle, studyDto, userIds);
                    return new ArrayDeque<>(extract);
                });
                fetched += currentBatch.size();
                if (currentBatch.isEmpty()) {
                    currentBatch = null;
                    exhausted = true;
                }
            }

            return currentBatch != null && !currentBatch.isEmpty();
        }

        @Override
        public Participant next() {
            if (currentBatch == null || currentBatch.isEmpty()) {
                throw new NoSuchElementException();
            }

            Participant next = currentBatch.remove();
            if (currentBatch.isEmpty()) {
                currentBatch = null;
            }

            return next;
        }

        @Override
        public void remove() {
            // Item is immediately removed from underlying collection in the `next()` call
            // in order to save on memory, so nothing else to do here.
        }
    }
}
