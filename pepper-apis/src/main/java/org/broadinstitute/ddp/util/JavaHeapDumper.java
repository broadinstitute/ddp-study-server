package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.management.MBeanServer;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.sun.management.HotSpotDiagnosticMXBean;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JavaHeapDumper {
    private static Logger LOG = LoggerFactory.getLogger(JavaHeapDumper.class);
    public static final String DEFAULT_LOCAL_PATH = "/tmp";
    public static final String DEFAULT_BUCKET_PATH = "heap_dumps";

    public void dumpHeapToLocalFile(String filePath) throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        LOG.info("About to create local file at:" + filePath);
        mxBean.dumpHeap(filePath, true);
    }

    public void dumpHeapToBucket(String projectId, String bucketName) throws IOException, DDPException {
        dumpHeapToBucket(projectId, bucketName, generateDumpFileName());
    }

    public void dumpHeapToBucket(String projectId, String bucketName, final String fileName) throws IOException {
        GoogleCredentials googleCredentials = GoogleCredentialUtil.initCredentials(true);
        Storage storage = GoogleBucketUtil.getStorage(googleCredentials, projectId);

        dumpHeapToLocalFile(DEFAULT_LOCAL_PATH + "/" + fileName);
        File localDumpFile = Paths.get(DEFAULT_LOCAL_PATH, fileName).toFile();
        if (localDumpFile.exists()) {
            LOG.info("Created local dump file: " + localDumpFile.getAbsolutePath() + " with size: " + localDumpFile.length());
        } else {
            LOG.error("Could not find dump file at:" + DEFAULT_LOCAL_PATH + "/" + fileName);
            throw new DDPException("Could not locate local dump file");
        }
        try (FileInputStream localDumpFileStream = new FileInputStream(localDumpFile)) {
            Blob blob = GoogleBucketUtil.uploadFile(storage, bucketName, DEFAULT_BUCKET_PATH + "/" + fileName,
                    "application/octet-stream", localDumpFileStream);
            LOG.info("Heap dump saved to bucket:" + bucketName + " to path: " + DEFAULT_BUCKET_PATH + "/" + fileName);
        } catch (DDPException e) {
            LOG.error("Could not upload the dump file at: " + fileName, e);
            throw e;
        } finally {
            if (localDumpFile.exists()) {
                localDumpFile.delete();
            }
        }

    }

    private String generateDumpFileName() {
        String gcpFileName = generateGcpFileName();
        return gcpFileName != null ? gcpFileName : nonGcpFileName();
    }

    private String generateGcpFileName() {
        String appName = System.getenv("GAE_APPLICATION");
        String serviceName = System.getenv("GAE_SERVICE");
        String instanceId = System.getenv("GAE_INSTANCE");
        String dateTime = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
        return appName != null ? appName + "-" + serviceName + "-" + instanceId + "-" + dateTime + ".hprof" : null;
    }

    private String nonGcpFileName() {
        return "heapDump-" + DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()) + ".hprof";
    }
}
