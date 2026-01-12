package mx.ipn.escom.auditservice;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuditGcsJsonlSink {

    private static final Logger log = LoggerFactory.getLogger(AuditGcsJsonlSink.class);

    @Value("${gcp.gcs.bucket}")
    private String bucket;

    @Value("${gcp.gcs.prefix:auditoria}")
    private String prefix;

    @Value("${audit.local-dir:./audit-buffer}")
    private String localDir;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    private ScheduledExecutorService flusher;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(Paths.get(localDir));
        flusher = Executors.newSingleThreadScheduledExecutor(new NamedNonDaemonFactory("audit-gcs-flusher"));
        flusher.scheduleWithFixedDelay(this::safeFlush, 3, 3, TimeUnit.SECONDS);
        log.info("✔ AuditGcsJsonlSink listo. bucket={}, prefix={}, localDir={}", bucket, prefix, localDir);
    }

    public synchronized void appendJsonl(String jsonLine) throws Exception {
        String day = LocalDate.now().toString();
        Path file = Paths.get(localDir, day + ".jsonl");

        Files.writeString(
                file,
                jsonLine + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        );

        dirty.set(true);
    }

    private void safeFlush() {
        try {
            flushIfDirty();
        } catch (Exception e) {
            log.error("❌ Error flush a GCS", e);
        }
    }

    public synchronized void flushIfDirty() throws Exception {
        if (!dirty.get()) return;

        String day = LocalDate.now().toString();
        Path file = Paths.get(localDir, day + ".jsonl");
        if (!Files.exists(file)) return;

        byte[] bytes = Files.readAllBytes(file);

        // ✅ REQUERIDO por tu bucket: auditoria/YYYY-MM-DD.jsonl
        String objectName = prefix + "/" + day + ".jsonl";

        BlobId blobId = BlobId.of(bucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/x-ndjson") // JSONL/NDJSON
                .build();

        storage.create(blobInfo, bytes);
        dirty.set(false);

        log.info("☁️ Subido a GCS: gs://{}/{} ({} bytes)", bucket, objectName, bytes.length);
    }

    @PreDestroy
    public void shutdown() {
        try { flushIfDirty(); } catch (Exception ignored) {}

        if (flusher != null) {
            flusher.shutdown();
            try { flusher.awaitTermination(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            flusher.shutdownNow();
        }
    }

    private static class NamedNonDaemonFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger n = new AtomicInteger(1);
        NamedNonDaemonFactory(String base) { this.base = base; }
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, base + "-" + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
