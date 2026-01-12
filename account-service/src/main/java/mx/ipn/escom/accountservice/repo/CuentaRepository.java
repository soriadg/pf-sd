package mx.ipn.escom.accountservice.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class CuentaRepository {

    private final JdbcTemplate jdbc;

    public CuentaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** En tu BD la cuenta se crea por trigger al insertar usuario. */
    public boolean existeCuenta(String curp) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cuentas WHERE curp = ?",
                Integer.class,
                curp
        );
        return n != null && n > 0;
    }

    /** Regresa saldo TOTAL = billetera + banco (para /balance). */
    public Optional<BigDecimal> obtenerSaldoTotal(String curp) {
        var list = jdbc.query("""
                SELECT (saldo_billetera + saldo_banco) AS saldo_total
                FROM cuentas
                WHERE curp = ?
                """,
                (rs, rowNum) -> rs.getBigDecimal("saldo_total"),
                curp
        );
        return list.stream().findFirst();
    }

    /** Bloquea fila para concurrencia segura. */
    public BigDecimal obtenerSaldoBilleteraForUpdate(String curp) {
        return jdbc.queryForObject(
                "SELECT saldo_billetera FROM cuentas WHERE curp = ? FOR UPDATE",
                BigDecimal.class,
                curp
        );
    }

    public int actualizarSaldoBilletera(String curp, BigDecimal nuevoSaldo) {
        return jdbc.update("""
                UPDATE cuentas
                   SET saldo_billetera = ?, actualizado_en = NOW()
                 WHERE curp = ?
                """, nuevoSaldo, curp);
    }

    // --- Si después quieres operar banco también ---
    public BigDecimal obtenerSaldoBancoForUpdate(String curp) {
        return jdbc.queryForObject(
                "SELECT saldo_banco FROM cuentas WHERE curp = ? FOR UPDATE",
                BigDecimal.class,
                curp
        );
    }

    public int actualizarSaldoBanco(String curp, BigDecimal nuevoSaldo) {
        return jdbc.update("""
                UPDATE cuentas
                   SET saldo_banco = ?, actualizado_en = NOW()
                 WHERE curp = ?
                """, nuevoSaldo, curp);
    }
}
