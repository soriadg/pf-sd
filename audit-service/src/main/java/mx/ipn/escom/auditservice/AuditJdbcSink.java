package mx.ipn.escom.auditservice;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

@Component
public class AuditJdbcSink {

    private final DataSource dataSource;

    public AuditJdbcSink(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(String eventId, String tipoEvento, String payloadJson) throws Exception {

        String sql =
                "INSERT INTO auditoria (transaccion_id, tipo_evento, payload_json) " +
                        "VALUES ((SELECT id FROM transacciones WHERE event_id = ?), ?, ?::jsonb)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, eventId);
            ps.setString(2, tipoEvento);
            ps.setString(3, payloadJson);
            ps.executeUpdate();
        }
    }
}
