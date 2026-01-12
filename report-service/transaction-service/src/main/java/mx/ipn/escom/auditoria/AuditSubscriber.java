package mx.ipn.escom.auditoria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
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

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    private Subscriber subscriber;
    private ScheduledExecutorService subscriberExecutor;
    private ExecutorService listenerExecutor;

    public AuditSubscriber(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // =========================
    // INICIO
    // =========================
    @PostConstruct
    public void iniciar() {

        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            try {
                procesarMensaje(message);
                consumer.ack();
            } catch (Exception e) {
                log.error("❌ Error en auditoría, NACK para reintento", e);
                consumer.nack();
            }
        };

        subscriberExecutor = Executors.newScheduledThreadPool(
                2,
                new NamedNonDaemonFactory("pubsub-audit")
        );

        listenerExecutor = Executors.newSingleThreadExecutor(
                new NamedNonDaemonFactory("pubsub-audit-listener")
        );

        subscriber = Subscriber.newBuilder(subscriptionName, receiver)
                .setExecutorProvider(FixedExecutorProvider.create(subscriberExecutor))
                .build();

        subscriber.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                log.error("❌ AuditSubscriber falló. Estado previo: {}", from, failure);
            }
        }, listenerExecutor);

        subscriber.startAsync().awaitRunning();
        log.info("✔ AuditService escuchando eventos confirmados...");
    }

    // =========================
    // APAGADO LIMPIO
    // =========================
    @PreDestroy
    public void detener() {
        try {
            if (subscriber != null) {
                subscriber.stopAsync();
                subscriber.awaitTerminated(10, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}

        apagar(subscriberExecutor);
        apagar(listenerExecutor);
    }

    private void apagar(ExecutorService exec) {
        if (exec == null) return;
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (Exception e) {
            exec.shutdownNow();
        }
    }

    // =========================
    // LÓGICA PRINCIPAL
    // =========================
    private void procesarMensaje(PubsubMessage message) throws Exception {

        String json = message.getData().toStringUtf8();
        JsonNode root = mapper.readTree(json);

        String eventId = root.get("event_id").asText();
        String tipo = root.get("tipo").asText();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO auditoria (transaccion_id, tipo_evento, payload_json) " +
                             "VALUES ((SELECT id FROM transacciones WHERE event_id = ?), ?, ?::jsonb)"
             )) {

            ps.setString(1, eventId);
            ps.setString(2, tipo + "_CONFIRMADA");
            ps.setString(3, json);
            ps.executeUpdate();
        }

        log.info("📝 Auditoría guardada para event_id={}", eventId);
    }

    // =========================
    // THREAD FACTORY
    // =========================
    private static class NamedNonDaemonFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger n = new AtomicInteger(1);

        private NamedNonDaemonFactory(String base) {
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
