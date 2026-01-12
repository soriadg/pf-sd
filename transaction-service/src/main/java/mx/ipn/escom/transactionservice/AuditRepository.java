package mx.ipn.escom.transactionservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(UUID txId, String tipoEvento, String payloadJson) {
        jdbc.update("""
            INSERT INTO auditoria(transaccion_id, tipo_evento, payload_json)
            VALUES (?, ?, ?::jsonb)
        """, txId, tipoEvento, payloadJson);
    }
}
