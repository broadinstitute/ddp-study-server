package org.broadinstitute.ddp.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;

@Slf4j
public class JavaHeapDumper {

    private static final String OPENJ9_MXBEAN_NAME = "openj9.lang.management:type=OpenJ9Diagnostics";
    private static final String HOTSPOT_MXBEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    public static final String DEFAULT_LOCAL_PATH = "/tmp";
    public static final String DEFAULT_BUCKET_PATH = "heap_dumps";

    public class JavaHeapDumpException extends DDPException {
        public JavaHeapDumpException(Throwable cause) {
            super(cause);
        }

        public JavaHeapDumpException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean dumpHeapPlatform(String mbeanName, String diagnosticBeanName, String fileName, boolean live) throws IOException {
        Class<? extends PlatformManagedObject> clazz = null;
        try {
            // If unchecked cast warnings were not suppressed, this is where the unchecked warning
            // would be generated. Disabling warnings should be a last resort, so if you have
            // an alternative that doesn't generate a warning- please #refactor me!
            clazz = (Class<? extends PlatformManagedObject>)Class.forName(diagnosticBeanName);
        } catch (ClassNotFoundException ex) {
            log.error("failed to load platform diagnostic bean '{}': {}", diagnosticBeanName, ex);
            return false;
        }

        var mbeanServer = ManagementFactory.getPlatformMBeanServer();
        PlatformManagedObject mxBean = null;
        
        try {
            mxBean = ManagementFactory.newPlatformMXBeanProxy(mbeanServer, mbeanName, clazz);
        } catch (IOException ex) {
            log.error("failed to create platform MX bean proxy: {}, skipping heap dump", ex);
            return false;
        }

        try {
            var methodImpl = clazz.getMethod("dumpHeap", String.class, boolean.class);
            methodImpl.invoke(mxBean, fileName, live);
        } catch (NoSuchMethodException nsme) {
            log.error("MX Bean {} does not implement dumpHeap(String, boolean), skipping heap dump", diagnosticBeanName);

            // At this point, it's not clear if this error rises to the level
            // of throwing a new runtime exception, and rethrowing this exception
            // is not an option as it's an implementation detail which callers
            // should not be aware of. Fail with a false (with a log entry) for now, but
            // consider changing this to wrap & throw as a runtime exception if needs
            // change.
            return false;
        } catch (IllegalAccessException iae) {
            log.error("MX Bean {} does not allow access to dumpHeap(String, boolean), skipping heap dump", diagnosticBeanName);

            // See the previous comment in the NoSuchMethodException handler.
            return false;
        } catch (InvocationTargetException ite) {
            var cause = ite.getCause();
            if (cause == null) {
                // The cause may be null, not sure what sort of error conditions that would
                // result from, however.
                log.error("an internal error occurred while dumping the heap: {}", ite.getMessage());
                throw new JavaHeapDumpException(ite);
            }
            // InvocationTargetException serves to wrap any checked exceptions thrown
            // by reflection. In this case, heapDump(String, boolean) is declared to
            // throw an IOException if the file fails to be written, so expose that to
            // the caller. If any other error occurs, log it and bail- something unchecked
            // happened.
            if (cause instanceof IOException) {
                throw IOException.class.cast(ite.getCause());
            } else {
                log.error("an error occurred while dumping the heap: {}", cause.getMessage());
                throw new JavaHeapDumpException(cause);
            }
        }

        return true;
    }

    public boolean dumpHeap(String fileName) throws IOException {
        var platformDiagnostics = new HashMap<String, String>();
        platformDiagnostics.put(JavaHeapDumper.OPENJ9_MXBEAN_NAME, "openj9.lang.management.OpenJ9DiagnosticsMXBean");
        platformDiagnostics.put(JavaHeapDumper.HOTSPOT_MXBEAN_NAME, "com.sun.management.HotSpotDiagnosticMXBean");

        for (var diagnosticNames : platformDiagnostics.entrySet()) {
            ObjectName diagnosticBeanName;

            try {
                diagnosticBeanName = new ObjectName(diagnosticNames.getKey());
            } catch (MalformedObjectNameException ex) {
                log.error("Malformed javax.management object name: {}", ex);
                throw new JavaHeapDumpException(ex);
            }

            if (ManagementFactory.getPlatformMBeanServer().isRegistered(diagnosticBeanName)) {
                final var mxBeanName = diagnosticNames.getValue();
                log.info("Using diagnostics mxbean {}", mxBeanName);

                return dumpHeapPlatform(diagnosticBeanName.getCanonicalName(), mxBeanName, fileName, true);
            }
        }

        return false;
    }

    public void dumpHeapToBucket(String projectId, String bucketName) throws IOException, DDPException {
        dumpHeapToBucket(projectId, bucketName, generateDumpFileName());
    }

    public void dumpHeapToBucket(String projectId, String bucketName, final String fileName) throws IOException {
        var googleCredentials = GoogleCredentialUtil.initCredentials(true);
        var storage = GoogleBucketUtil.getStorage(googleCredentials, projectId);

        dumpHeap(DEFAULT_LOCAL_PATH + "/" + fileName);
        var localDumpFile = Paths.get(DEFAULT_LOCAL_PATH, fileName).toFile();
        if (localDumpFile.exists()) {
            log.info("Created local dump file: {} with size: {}",
                    localDumpFile.getAbsolutePath(),
                    localDumpFile.length());
        } else {
            log.error("Could not find dump file at: {}/{}", DEFAULT_LOCAL_PATH, fileName);
            throw new JavaHeapDumpException("Could not locate local dump file");
        }
        try (FileInputStream localDumpFileStream = new FileInputStream(localDumpFile)) {
            GoogleBucketUtil.uploadFile(storage, bucketName, DEFAULT_BUCKET_PATH + "/" + fileName,
                    "application/octet-stream", localDumpFileStream);
            log.info("Heap dump saved to bucket: {} to path: {}/{}", bucketName, DEFAULT_BUCKET_PATH, fileName);
        } catch (DDPException e) {
            log.error("Could not upload the dump file at: {}", fileName, e);
            throw e;
        } finally {
            if (localDumpFile.exists()) {
                Files.delete(localDumpFile.toPath());
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
