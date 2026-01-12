package mx.ipn.escom.transactionservice;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserta la transacción si NO existe (idempotencia por event_id UNIQUE).
     * IMPORTANTE: Debe cumplir el CHECK chk_tx_campos_por_tipo de tu BD.
     */
    public void insertIfAbsent(String eventId, TxType tipo, String curpOrigen, String curpDestino, BigDecimal monto) {
        // Normalización para cumplir constraints:
        // - DEPOSITO: curp_origen NULL, curp_destino = usuario
        // - RETIRO: curp_origen = usuario, curp_destino NULL
        // - TRANSFERENCIA: ambos no null y diferentes
        String o = curpOrigen;
        String d = curpDestino;

        if (tipo == TxType.DEPOSITO) {
            if (d == null && o != null) d = o;   // tolera si te mandan en curp_origen
            o = null;
        } else if (tipo == TxType.RETIRO) {
            if (o == null && d != null) o = d;   // tolera si te mandan en curp_destino
            d = null;
        }

        jdbc.update("""
            INSERT INTO transacciones(event_id, curp_origen, curp_destino, monto, tipo, estado)
            VALUES (?, ?, ?, ?, ?, 'PENDIENTE')
            ON CONFLICT (event_id) DO NOTHING
        """, eventId, o, d, monto, tipo.name());
    }

    /** Bloquea la transacción por event_id para evitar que dos réplicas apliquen balances. */
    public TxRow lockByEventId(String eventId) {
        try {
            return jdbc.queryForObject("""
                SELECT id, event_id, tipo, curp_origen, curp_destino, monto, estado
                FROM transacciones
                WHERE event_id = ?
                FOR UPDATE
            """, (rs, rowNum) -> new TxRow(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("event_id"),
                    TxType.from(rs.getString("tipo")),
                    rs.getString("curp_origen"),
                    rs.getString("curp_destino"),
                    rs.getBigDecimal("monto"),
                    rs.getString("estado")
            ), eventId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int markConfirmed(String eventId) {
        return jdbc.update("""
            UPDATE transacciones
            SET estado = 'CONFIRMADA',
                confirmado_en = COALESCE(confirmado_en, NOW())
            WHERE event_id = ? AND estado <> 'CONFIRMADA'
        """, eventId);
    }

    public int markFailed(String eventId) {
        return jdbc.update("""
            UPDATE transacciones
            SET estado = 'FALLIDA',
                confirmado_en = COALESCE(confirmado_en, NOW())
            WHERE event_id = ? AND estado <> 'FALLIDA'
        """, eventId);
    }
}
