package org.broadinstitute.ddp.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;

/**
 * A helper for reading contents of data files. This encapsulates complexity of working with buckets versus filesystem.
 */
@Slf4j
class FileReader {
    private final boolean useBucket;
    private final String mailingListFilePrefix;
    private final String participantFilePrefix;
    private final String familyMemberFilePrefix;
    private final Set<String> mailingListFiles = new HashSet<>();
    private final Set<String> participantFiles = new HashSet<>();
    private final Set<String> familyMemberFiles = new HashSet<>();
    private boolean cached;
    private String bucketName;
    private String bucketDir;
    private Storage storage;
    private String localDir;

    FileReader(Config cfg) {
        this.useBucket = cfg.getBoolean(LoaderConfigFile.SOURCE_USE_BUCKET);
        this.mailingListFilePrefix = cfg.getString(LoaderConfigFile.SOURCE_MAILING_LIST_FILE_PREFIX);
        this.participantFilePrefix = cfg.getString(LoaderConfigFile.SOURCE_PARTICIPANT_FILE_PREFIX);
        this.familyMemberFilePrefix = cfg.getString(LoaderConfigFile.SOURCE_FAMILY_MEMBER_FILE_PREFIX);
        if (useBucket) {
            this.bucketName = cfg.getString(LoaderConfigFile.SOURCE_BUCKET_NAME);
            this.bucketDir = cfg.getString(LoaderConfigFile.SOURCE_BUCKET_DIR);
            this.storage = initStorageService(cfg);
        } else {
            this.localDir = cfg.getString(LoaderConfigFile.SOURCE_LOCAL_DIR);
        }
        checkSourceLocation();
    }

    private Storage initStorageService(Config cfg) {
        try {
            var projectId = cfg.getString(LoaderConfigFile.SOURCE_PROJECT_ID);
            var credentialsFile = cfg.getString(LoaderConfigFile.SOURCE_CREDENTIALS_FILE);
            var credentials = ServiceAccountCredentials.fromStream(new FileInputStream(credentialsFile));
            return StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build().getService();
        } catch (Exception e) {
            throw new LoaderException("Error while initializing storage service", e);
        }
    }

    private void checkSourceLocation() {
        if (useBucket) {
            Bucket bucket = storage.get(bucketName);
            if (bucket == null) {
                throw new LoaderException("Bucket does not exist: " + bucketName);
            } else {
                log.info("Using bucket for source files: gs://{}/{}", bucketName, bucketDir);
            }
        } else {
            File folder = new File(localDir);
            if (!folder.exists() || !folder.isDirectory()) {
                throw new LoaderException("Local directory does not exist: " + localDir);
            } else {
                log.info("Using local directory for source files: {}", localDir);
            }
        }
    }

    private Stream<String> streamFilesFromSource() {
        if (useBucket) {
            Bucket bucket = storage.get(bucketName);
            var options = Storage.BlobListOption.prefix(bucketDir);
            Spliterator<Blob> iterator = bucket.list(options).iterateAll().spliterator();
            return StreamSupport
                    .stream(iterator, false)
                    .filter(blob -> !blob.isDirectory())
                    .map(BlobInfo::getName);
        } else {
            File folder = new File(localDir);
            File[] files = folder.listFiles();
            if (files == null) {
                throw new LoaderException("Error while list files from load directory: " + localDir);
            }
            return Stream.of(files)
                    .filter(File::isFile)
                    .map(File::getName);
        }
    }

    private void listAndCacheFilesFromSource() {
        streamFilesFromSource().forEach(path -> {
            String filename = Path.of(path).getFileName().toString();
            if (filename.startsWith(mailingListFilePrefix)) {
                mailingListFiles.add(filename);
            } else if (filename.startsWith(participantFilePrefix)) {
                participantFiles.add(filename);
            } else if (filename.startsWith(familyMemberFilePrefix)) {
                familyMemberFiles.add(filename);
            }
        });
        cached = true;
    }

    public Set<String> listMailingListFiles() {
        if (!cached) {
            listAndCacheFilesFromSource();
        }
        return mailingListFiles;
    }

    public Set<String> listParticipantFiles() {
        if (!cached) {
            listAndCacheFilesFromSource();
        }
        return participantFiles;
    }

    public Set<String> listFamilyMemberFiles() {
        if (!cached) {
            listAndCacheFilesFromSource();
        }
        return familyMemberFiles;
    }

    public Reader readContent(String filename) {
        if (useBucket) {
            Path path = Path.of(bucketDir, filename);
            Blob blob = storage.get(bucketName, path.toString());
            return Channels.newReader(blob.reader(), StandardCharsets.UTF_8);
        } else {
            try {
                return Files.newBufferedReader(Path.of(localDir, filename));
            } catch (IOException e) {
                throw new LoaderException(e);
            }
        }
    }
}
