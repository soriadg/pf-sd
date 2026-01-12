package mx.ipn.escom.transaction;

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
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final DataSource dataSource;
    private final PublicadorConfirmaciones publicadorConfirmaciones;
    private final ObjectMapper mapper = new ObjectMapper();

    private Subscriber subscriber;
    private ScheduledExecutorService subscriberExecutor;
    private ExecutorService listenerExecutor;

    public TransactionSubscriber(DataSource dataSource, PublicadorConfirmaciones publicadorConfirmaciones) {
        this.dataSource = dataSource;
        this.publicadorConfirmaciones = publicadorConfirmaciones;
    }

    @PostConstruct
    public void iniciarSuscriptor() {
        ProjectSubscriptionName subscriptionName =
                ProjectSubscriptionName.of(projectId, subscriptionId);

        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            try {
                procesarMensaje(message);
                consumer.ack(); // ✅ ACK solo si TODO salió bien (incluye COMMIT)
            } catch (Exception e) {
                log.error("❌ Error procesando mensaje Pub/Sub. Se enviará NACK (reintento).", e);
                consumer.nack();
            }
        };

        subscriberExecutor = Executors.newScheduledThreadPool(
                4,
                new NamedNonDaemonFactory("pubsub-tx-subscriber")
        );

        listenerExecutor = Executors.newSingleThreadExecutor(
                new NamedNonDaemonFactory("pubsub-tx-listener")
        );

        subscriber = Subscriber.newBuilder(subscriptionName, receiver)
                .setExecutorProvider(FixedExecutorProvider.create(subscriberExecutor))
                .build();

        subscriber.addListener(new ApiService.Listener() {
            @Override
            public void failed(ApiService.State from, Throwable failure) {
                log.error("❌ Subscriber falló. Estado previo: {}", from, failure);
            }
        }, listenerExecutor);

        subscriber.startAsync().awaitRunning();
        log.info("✔ TransactionService escuchando mensajes de Pub/Sub...");
    }

    @PreDestroy
    public void detenerSuscriptor() {
        try {
            if (subscriber != null) {
                subscriber.stopAsync();
                try { subscriber.awaitTerminated(10, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("⚠ No se pudo detener el subscriber limpiamente.", e);
        }

        tryShutdown(subscriberExecutor, "subscriberExecutor");
        tryShutdown(listenerExecutor, "listenerExecutor");
    }

    private void tryShutdown(ExecutorService exec, String name) {
        if (exec == null) return;
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) exec.shutdownNow();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            exec.shutdownNow();
        } catch (Exception e) {
            log.warn("⚠ Error apagando {}", name, e);
        }
    }

    private void procesarMensaje(PubsubMessage message) throws Exception {

        final String json = message.getData().toStringUtf8();
        final JsonNode root = mapper.readTree(json);

        if (root.get("event_id") == null || root.get("tipo") == null ||
                root.get("curp_origen") == null || root.get("monto") == null) {
            throw new IllegalArgumentException("Payload inválido: faltan campos obligatorios. Payload=" + json);
        }

        final String eventId = root.get("event_id").asText();
        final String tipo = root.get("tipo").asText();
        final String curpOrigen = root.get("curp_origen").asText();

        JsonNode destinoNode = root.get("curp_destino");
        final String curpDestino = (destinoNode == null || destinoNode.isNull()) ? null : destinoNode.asText();

        final double monto = root.get("monto").asDouble();
        if (monto <= 0) throw new IllegalArgumentException("Monto inválido (<=0). event_id=" + eventId);

        try (Connection conn = obtenerConexion()) {
            conn.setAutoCommit(false);

            try {
                // ======================================================
                // 0) Asegurar fila + BLOQUEO (idempotencia para réplicas)
                // ======================================================
                // Inserta si no existe (si AccountService ya la insertó, no pasa nada)
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO transacciones (event_id, curp_origen, curp_destino, monto, tipo, estado) " +
                                "VALUES (?, ?, ?, ?, ?, 'PENDIENTE') " +
                                "ON CONFLICT (event_id) DO NOTHING")) {
                    ins.setString(1, eventId);
                    ins.setString(2, curpOrigen);
                    ins.setString(3, curpDestino);
                    ins.setDouble(4, monto);
                    ins.setString(5, tipo);
                    ins.executeUpdate();
                }

                String estado;
                try (PreparedStatement lock = conn.prepareStatement(
                        "SELECT estado FROM transacciones WHERE event_id = ? FOR UPDATE")) {
                    lock.setString(1, eventId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No se encontró transacción para event_id=" + eventId);
                        estado = rs.getString("estado");
                    }
                }

                if ("CONFIRMADA".equals(estado)) {
                    // Ya aplicada → idempotente
                    conn.commit();
                    log.info("ℹ Evento duplicado ignorado: {}", eventId);
                    return;
                }

                // ===============================
                // 1) Aplicar operación (con checks de saldo)
                // ===============================
                aplicarOperacion(conn, tipo, curpOrigen, curpDestino, monto);

                // ===============================
                // 2) Marcar CONFIRMADA (solo si no lo estaba)
                // ===============================
                int cambio;
                try (PreparedStatement confirmar = conn.prepareStatement(
                        "UPDATE transacciones " +
                                "SET estado = 'CONFIRMADA', confirmado_en = COALESCE(confirmado_en, NOW()) " +
                                "WHERE event_id = ? AND estado <> 'CONFIRMADA'")) {
                    confirmar.setString(1, eventId);
                    cambio = confirmar.executeUpdate();
                }

                // ===============================
                // 3) Auditoría en BD (opcional, pero útil)
                // ===============================
                try (PreparedStatement auditoria = conn.prepareStatement(
                        "INSERT INTO auditoria (transaccion_id, tipo_evento, payload_json) " +
                                "VALUES ((SELECT id FROM transacciones WHERE event_id = ?), ?, ?::jsonb)")) {
                    auditoria.setString(1, eventId);
                    auditoria.setString(2, "TRANSACCION_CONFIRMADA");
                    auditoria.setString(3, json);
                    auditoria.executeUpdate();
                }

                // ✅ Commit de BD primero
                conn.commit();

                // ======================================================
                // 4) Publicar confirmación a Pub/Sub (requisito)
                // ======================================================
                // Publicamos solo si hubo cambio (evita spam por duplicados)
                if (cambio > 0) {
                    publicadorConfirmaciones.publicarConfirmacion(eventId, tipo, curpOrigen, curpDestino, monto, json);
                }

                log.info("✅ Transacción confirmada: {} ({}, ${})", eventId, tipo, monto);

            } catch (Exception e) {
                try { conn.rollback(); } catch (Exception rb) { log.warn("⚠ Falló rollback para event_id={}", eventId, rb); }
                log.error("❌ Error procesando event_id={} → ROLLBACK", eventId, e);
                throw e;
            }
        }
    }

    private void aplicarOperacion(Connection conn,
                                  String tipo,
                                  String origen,
                                  String destino,
                                  double monto) throws SQLException {

        switch (tipo) {

            case "DEPOSITO" -> {
                // banco -> billetera (evita saldo_banco negativo)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE cuentas SET " +
                                "saldo_banco = saldo_banco - ?, " +
                                "saldo_billetera = saldo_billetera + ? " +
                                "WHERE curp = ? AND saldo_banco >= ?")) {
                    ps.setDouble(1, monto);
                    ps.setDouble(2, monto);
                    ps.setString(3, origen);
                    ps.setDouble(4, monto);
                    int updated = ps.executeUpdate();
                    if (updated != 1) throw new SQLException("Fondos insuficientes o cuenta no encontrada (DEPOSITO): " + origen);
                }
            }

            case "RETIRO" -> {
                // billetera -> banco (evita saldo_billetera negativo)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE cuentas SET " +
                                "saldo_billetera = saldo_billetera - ?, " +
                                "saldo_banco = saldo_banco + ? " +
                                "WHERE curp = ? AND saldo_billetera >= ?")) {
                    ps.setDouble(1, monto);
                    ps.setDouble(2, monto);
                    ps.setString(3, origen);
                    ps.setDouble(4, monto);
                    int updated = ps.executeUpdate();
                    if (updated != 1) throw new SQLException("Fondos insuficientes o cuenta no encontrada (RETIRO): " + origen);
                }
            }

            case "TRANSFERENCIA" -> {
                if (destino == null || destino.isBlank()) {
                    throw new SQLException("TRANSFERENCIA sin curp_destino");
                }

                // 1) descontar a origen (con saldo suficiente)
                try (PreparedStatement ps1 = conn.prepareStatement(
                        "UPDATE cuentas SET saldo_billetera = saldo_billetera - ? " +
                                "WHERE curp = ? AND saldo_billetera >= ?")) {
                    ps1.setDouble(1, monto);
                    ps1.setString(2, origen);
                    ps1.setDouble(3, monto);
                    int u1 = ps1.executeUpdate();
                    if (u1 != 1) throw new SQLException("Fondos insuficientes o cuenta origen no encontrada (TRANSFERENCIA): " + origen);
                }

                // 2) sumar a destino
                try (PreparedStatement ps2 = conn.prepareStatement(
                        "UPDATE cuentas SET saldo_billetera = saldo_billetera + ? WHERE curp = ?")) {
                    ps2.setDouble(1, monto);
                    ps2.setString(2, destino);
                    int u2 = ps2.executeUpdate();
                    if (u2 != 1) throw new SQLException("Cuenta destino no encontrada (TRANSFERENCIA): " + destino);
                }
            }

            default -> throw new SQLException("Tipo de operación inválido: " + tipo);
        }
    }

    private Connection obtenerConexion() throws SQLException {
        return dataSource.getConnection();
    }

    private static class NamedNonDaemonFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger n = new AtomicInteger(1);

        private NamedNonDaemonFactory(String base) { this.base = base; }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, base + "-" + n.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
