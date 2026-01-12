package mx.ipn.escom.transactionservice;

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

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TransactionSubscriber {

    private static final Logger log = LoggerFactory.getLogger(TransactionSubscriber.class);

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.pubsub.subscription-id}")
    private String subscriptionId;

    private final TransactionProcessor processor;
    private final PublicadorConfirmaciones publicador;

    private final ObjectMapper mapper = new ObjectMapper();

    private Subscriber subscriber;
    private ScheduledExecutorService subscriberExecutor;
    private ExecutorService listenerExecutor;

    public TransactionSubscriber(TransactionProcessor processor, PublicadorConfirmaciones publicador) {
        this.processor = processor;
        this.publicador = publicador;
    }

    @PostConstruct
    public void iniciar() {
        ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            String payloadOriginal = message.getData().toStringUtf8();
            try {
                // 1) Parsear request
                JsonNode root = mapper.readTree(payloadOriginal);
                Map<String, String> attrs = message.getAttributesMap();

                // eventId puede venir como event_id o txId
                String eventId = firstNonBlank(
                        attrs.get("event_id"),
                        attrs.get("txId"),
                        text(root, "event_id"),
                        text(root, "txId"),
                        text(root, "id")
                );

                String tipoStr = firstNonBlank(
                        attrs.get("tipo"),
                        text(root, "tipo"),
                        text(root, "type")
                );

                // curps pueden venir como curp_origen/curp_destino o fromCurp/toCurp
                String curpOrigen = firstNonBlank(
                        text(root, "curp_origen"),
                        text(root, "fromCurp"),
                        attrs.get("curp_origen"),
                        attrs.get("fromCurp")
                );

                String curpDestino = firstNonBlank(
                        text(root, "curp_destino"),
                        text(root, "toCurp"),
                        attrs.get("curp_destino"),
                        attrs.get("toCurp")
                );

                // monto puede venir como monto o amount
                String montoStr = firstNonBlank(
                        text(root, "monto"),
                        text(root, "amount"),
                        attrs.get("monto"),
                        attrs.get("amount")
                );

                if (eventId == null || eventId.isBlank() || tipoStr == null || tipoStr.isBlank() || montoStr == null || montoStr.isBlank()) {
                    // Mensaje malformado => ACK para que NO se quede en loop
                    log.error("❌ Mensaje inválido (sin eventId/tipo/monto). Se ACK para evitar reintento infinito. msgId={} payload={}",
                            message.getMessageId(), payloadOriginal);
                    consumer.ack();
                    return;
                }

                BigDecimal monto = new BigDecimal(montoStr);
                TxType tipo = TxType.from(tipoStr);

                // 2) Procesar en BD (idempotente)
                TxRow result = processor.procesarEnBD(eventId, tipo, curpOrigen, curpDestino, monto, payloadOriginal);

                // 3) Si quedó CONFIRMADA => publicar a tx-confirmed
                if ("CONFIRMADA".equalsIgnoreCase(result.estado())) {
                    publicador.publicarConfirmacion(
                            result.eventId(),
                            result.tipo().name(),
                            result.curpOrigen(),
                            result.curpDestino(),
                            result.monto(),
                            payloadOriginal
                    );
                    log.info("✔ Publicada confirmación a tx-confirmed. event_id={}", result.eventId());
                } else {
                    log.info("ℹ Transacción no confirmada (estado={}). No se publica tx-confirmed. event_id={}",
                            result.estado(), result.eventId());
                }

                consumer.ack();
            } catch (Exception e) {
                log.error("❌ Error procesando mensaje tx-events. NACK (reintento). msgId={} payload={}",
                        message.getMessageId(), payloadOriginal, e);
                consumer.nack();
            }
        };

        subscriberExecutor = Executors.newScheduledThreadPool(
                4, new NamedNonDaemonFactory("pubsub-tx")
        );

        listenerExecutor = Executors.newSingleThreadExecutor(
                new NamedNonDaemonFactory("pubsub-tx-listener")
        );

        subscriber = Subscriber.newBuilder(subName, receiver)
                .setExecutorProvider(FixedExecutorProvider.create(subscriberExecutor))
                .build();

        subscriber.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                log.error("❌ TransactionSubscriber falló. Estado previo: {}", from, failure);
            }
        }, listenerExecutor);

        subscriber.startAsync().awaitRunning();
        log.info("✔ TransactionService escuchando subscription={} (project={})", subscriptionId, projectId);
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

    private String text(JsonNode node, String key) {
        if (node == null) return null;
        JsonNode n = node.get(key);
        if (n == null || n.isNull()) return null;
        String v = n.asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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
