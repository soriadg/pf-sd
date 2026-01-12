package mx.ipn.escom.accountservice.repo;

import mx.ipn.escom.accountservice.model.StatusTransaccion;
import mx.ipn.escom.accountservice.model.TipoTransaccion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class TransaccionRepository {

    private final JdbcTemplate jdbc;

    public TransaccionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserta una transacción acorde a tu BD.
     * - CONFIRMADA/FALLIDA => confirmadoEn NO NULL (por constraint)
     * - PENDIENTE => confirmadoEn NULL
     */
    public void insertar(UUID id,
                         String eventId,
                         TipoTransaccion tipo,
                         StatusTransaccion estado,
                         String curpOrigen,
                         String curpDestino,
                         BigDecimal monto,
                         Instant confirmadoEn) {

        jdbc.update("""
            INSERT INTO transacciones(id, event_id, curp_origen, curp_destino, monto, tipo, estado, confirmado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
                id,
                eventId,
                curpOrigen,
                curpDestino,
                monto,
                tipo.db(),     // valores en español para tu CHECK
                estado.db(),   // valores en español para tu CHECK
                (confirmadoEn == null ? null : Timestamp.from(confirmadoEn))
        );
    }
}
