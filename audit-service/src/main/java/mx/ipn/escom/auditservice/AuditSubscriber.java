package mx.ipn.escom.auditservice;

import com.google.api.core.ApiService;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuditSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AuditSubscriber.class);

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.pubsub.audit-subscription-id}")
    private String subscriptionId;

    private final AuditHandler handler;

    private Subscriber subscriber;

    // ✅ FixedExecutorProvider.create(...) requiere ScheduledExecutorService
    private ScheduledExecutorService subscriberExecutor;
    private ExecutorService listenerExecutor;

    public AuditSubscriber(AuditHandler handler) {
        this.handler = handler;
    }

    @PostConstruct
    public void iniciar() {
        ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            try {
                handler.handle(message);
                consumer.ack();
            } catch (IllegalArgumentException bad) {
                // ✅ Mensaje inválido/ruidoso: ACK para evitar NACK infinito
                log.warn("⚠ Mensaje inválido, ACK para evitar loop. msgId={}, error={}",
                        message.getMessageId(), bad.getMessage());
                consumer.ack();
            } catch (Exception e) {
                // ✅ Fallo real (BD/GCS): NACK para reintento
                log.error("❌ Error AuditService -> NACK (reintento). msgId={}", message.getMessageId(), e);
                consumer.nack();
            }
        };

        // ✅ ScheduledThreadPool para cumplir con FixedExecutorProvider
        subscriberExecutor = Executors.newScheduledThreadPool(
                4,
                new NamedNonDaemonFactory("pubsub-audit")
        );

        listenerExecutor = Executors.newSingleThreadExecutor(
                new NamedNonDaemonFactory("pubsub-audit-listener")
        );

        subscriber = Subscriber.newBuilder(subName, receiver)
                .setExecutorProvider(FixedExecutorProvider.create(subscriberExecutor))
                .build();

        subscriber.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                log.error("❌ AuditSubscriber falló. Estado previo: {}", from, failure);
            }
        }, listenerExecutor);

        subscriber.startAsync().awaitRunning();
        log.info("✔ AuditService escuchando subscription={} (project={})", subscriptionId, projectId);
    }

    @PreDestroy
    public void detener() {
        try {
            if (subscriber != null) {
                subscriber.stopAsync();
                subscriber.awaitTerminated(10, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}

        shutdown(subscriberExecutor);
        shutdown(listenerExecutor);
    }

    private void shutdown(ExecutorService exec) {
        if (exec == null) return;
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) exec.shutdownNow();
        } catch (Exception e) {
            exec.shutdownNow();
        }
    }

    private static class NamedNonDaemonFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger n = new AtomicInteger(1);

        NamedNonDaemonFactory(String base) {
            this.base = base;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, base + "-" + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
