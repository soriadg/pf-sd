package mx.ipn.escom.cuentas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CuentaServicio {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.pubsub.topic-id}")
    private String topicId;

    private final DataSource dataSource;

    private Publisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public CuentaServicio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void iniciarPublisher() throws Exception {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
        publisher = Publisher.newBuilder(topicName).build();
        System.out.println("✔ CuentaServicio listo. Publicando en topic: " + topicId);
    }

    @PreDestroy
    public void cerrarPublisher() throws Exception {
        if (publisher != null) {
            publisher.shutdown();
            publisher.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    // =========================
    // BALANCE
    // =========================
    public Map<String, Object> obtenerBalance(String curp) {
        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT saldo_banco, saldo_billetera FROM cuentas WHERE curp = ?"
             )) {
            ps.setString(1, curp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExcepcionHttp(404, "Cuenta no encontrada para CURP: " + curp);
                }

                double saldoBanco = rs.getDouble("saldo_banco");
                double saldoBilletera = rs.getDouble("saldo_billetera");

                return Map.of(
                        "curp", curp,
                        "saldo_banco", saldoBanco,
                        "saldo_billetera", saldoBilletera,
                        "timestamp", Instant.now().toString()
                );
            }

        } catch (SQLException e) {
            throw new ExcepcionHttp(500, "Error consultando balance: " + e.getMessage());
        }
    }

    // =========================
    // DEPOSITO (Banco -> Billetera)
    // =========================
    public Map<String, Object> depositar(String curpOrigen, double monto) {
        validarMonto(monto);
        asegurarCuentaExiste(curpOrigen);

        String eventId = generarEventId();

        // 1) Guardar PENDIENTE primero (para trazabilidad)
        guardarPendiente(eventId, "DEPOSITO", curpOrigen, null, monto);

        // 2) Publicar evento
        publicarEvento(eventId, "DEPOSITO", curpOrigen, null, monto);

        return respuestaPendiente(eventId, "DEPOSITO", curpOrigen, null, monto);
    }

    // =========================
    // RETIRO (Billetera -> Banco)
    // =========================
    public Map<String, Object> retirar(String curpOrigen, double monto) {
        validarMonto(monto);
        asegurarCuentaExiste(curpOrigen);

        String eventId = generarEventId();

        guardarPendiente(eventId, "RETIRO", curpOrigen, null, monto);
        publicarEvento(eventId, "RETIRO", curpOrigen, null, monto);

        return respuestaPendiente(eventId, "RETIRO", curpOrigen, null, monto);
    }

    // =========================
    // TRANSFERENCIA (Billetera -> Billetera)
    // =========================
    public Map<String, Object> transferir(String curpOrigen, String curpDestino, double monto) {
        validarMonto(monto);

        if (curpDestino == null || curpDestino.isBlank()) {
            throw new ExcepcionHttp(400, "curpDestino es obligatorio");
        }
        if (curpOrigen.equals(curpDestino)) {
            throw new ExcepcionHttp(400, "No se permite transferir a la misma cuenta");
        }

        asegurarCuentaExiste(curpOrigen);
        asegurarCuentaExiste(curpDestino);

        String eventId = generarEventId();

        guardarPendiente(eventId, "TRANSFERENCIA", curpOrigen, curpDestino, monto);
        publicarEvento(eventId, "TRANSFERENCIA", curpOrigen, curpDestino, monto);

        return respuestaPendiente(eventId, "TRANSFERENCIA", curpOrigen, curpDestino, monto);
    }

    // =========================
    // ESTADO TRANSACCION
    // =========================
    public Map<String, Object> consultarEstado(String eventId) {
        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT event_id, tipo, estado, confirmado_en FROM transacciones WHERE event_id = ?"
             )) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Map.of("event_id", eventId, "estado", "NO_ENCONTRADA");
                }

                return Map.of(
                        "event_id", rs.getString("event_id"),
                        "tipo", rs.getString("tipo"),
                        "estado", rs.getString("estado"),
                        "confirmado_en", rs.getObject("confirmado_en")
                );
            }

        } catch (SQLException e) {
            throw new ExcepcionHttp(500, "Error consultando estado: " + e.getMessage());
        }
    }

    // =========================
    // Pub/Sub
    // =========================
    private void publicarEvento(String eventId, String tipo, String curpOrigen, String curpDestino, double monto) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_id", eventId);
            payload.put("tipo", tipo);
            payload.put("curp_origen", curpOrigen);
            payload.put("curp_destino", curpDestino); // puede ser null
            payload.put("monto", monto);
            payload.put("timestamp", Instant.now().toString());

            String json = mapper.writeValueAsString(payload);

            PubsubMessage msg = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(json))
                    .putAttributes("event_id", eventId)
                    .putAttributes("tipo", tipo)
                    .build();

            ApiFuture<String> fut = publisher.publish(msg);
            fut.get(10, TimeUnit.SECONDS);

        } catch (Exception e) {
            // Si la publicación falla, deja rastro en BD para debugging
            marcarFallida(eventId, "Error publicando a Pub/Sub: " + e.getMessage());
            throw new ExcepcionHttp(500, "Error publicando a Pub/Sub: " + e.getMessage());
        }
    }

    // =========================
    // BD: guardar PENDIENTE (idempotente)
    // =========================
    private void guardarPendiente(String eventId, String tipo, String origen, String destino, double monto) {
        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO transacciones (event_id, curp_origen, curp_destino, monto, tipo, estado) " +
                             "VALUES (?, ?, ?, ?, ?, 'PENDIENTE') " +
                             "ON CONFLICT (event_id) DO NOTHING"
             )) {
            ps.setString(1, eventId);
            ps.setString(2, origen);
            ps.setString(3, destino);
            ps.setDouble(4, monto);
            ps.setString(5, tipo);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Si falla, mejor detener: sin PENDIENTE pierdes trazabilidad
            throw new ExcepcionHttp(500, "No se pudo registrar la transacción PENDIENTE: " + e.getMessage());
        }
    }

    private void marcarFallida(String eventId, String motivo) {
        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE transacciones SET estado='FALLIDA' WHERE event_id = ?"
             )) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        } catch (Exception ignored) {
            // No bloquea respuesta; es solo para rastro
        }
        System.out.println("⚠ Transacción marcada FALLIDA: " + eventId + " motivo=" + motivo);
    }

    // =========================
    // Validaciones
    // =========================
    private void validarMonto(double monto) {
        if (monto <= 0) {
            throw new ExcepcionHttp(400, "El monto debe ser mayor a 0");
        }
    }

    private void asegurarCuentaExiste(String curp) {
        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM cuentas WHERE curp = ?")) {
            ps.setString(1, curp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ExcepcionHttp(404, "Cuenta no encontrada: " + curp);
                }
            }
        } catch (SQLException e) {
            throw new ExcepcionHttp(500, "Error validando cuenta: " + e.getMessage());
        }
    }

    // =========================
    // Helpers
    // =========================
    private String generarEventId() {
        return "tx-" + UUID.randomUUID();
    }

    private Map<String, Object> respuestaPendiente(String eventId, String tipo, String origen, String destino, double monto) {
        return Map.of(
                "event_id", eventId,
                "tipo", tipo,
                "curp_origen", origen,
                "curp_destino", destino,
                "monto", monto,
                "estado", "PENDIENTE",
                "mensaje", "Evento publicado. TransactionService confirmará y aplicará saldos."
        );
    }

    private Connection obtenerConexion() throws SQLException {
        return dataSource.getConnection();
    }
}
