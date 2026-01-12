package mx.ipn.escom.transactionservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lock determinista de 2 cuentas para evitar deadlocks */
    public void lockTwoAccounts(String curpA, String curpB) {
        List<String> locked = jdbc.query("""
            SELECT curp
            FROM cuentas
            WHERE curp IN (?, ?)
            ORDER BY curp
            FOR UPDATE
        """, (rs, rowNum) -> rs.getString("curp"), curpA, curpB);

        if (locked.size() != 2) {
            throw new BusinessException("Cuenta origen/destino no existe.");
        }
    }

    public void deposito(String curpUsuario, BigDecimal monto) {
        int updated = jdbc.update("""
            UPDATE cuentas
            SET saldo_banco = saldo_banco - ?,
                saldo_billetera = saldo_billetera + ?,
                actualizado_en = NOW()
            WHERE curp = ? AND saldo_banco >= ?
        """, monto, monto, curpUsuario, monto);

        if (updated != 1) {
            throw new BusinessException("Fondos insuficientes o cuenta no encontrada (DEPOSITO)");
        }
    }

    public void retiro(String curpUsuario, BigDecimal monto) {
        int updated = jdbc.update("""
            UPDATE cuentas
            SET saldo_billetera = saldo_billetera - ?,
                saldo_banco = saldo_banco + ?,
                actualizado_en = NOW()
            WHERE curp = ? AND saldo_billetera >= ?
        """, monto, monto, curpUsuario, monto);

        if (updated != 1) {
            throw new BusinessException("Fondos insuficientes o cuenta no encontrada (RETIRO)");
        }
    }

    public void transferencia(String origen, String destino, BigDecimal monto) {
        if (origen.equals(destino)) {
            throw new BusinessException("TRANSFERENCIA no permite mismo origen y destino");
        }

        lockTwoAccounts(origen, destino);

        int u1 = jdbc.update("""
            UPDATE cuentas
            SET saldo_billetera = saldo_billetera - ?,
                actualizado_en = NOW()
            WHERE curp = ? AND saldo_billetera >= ?
        """, monto, origen, monto);

        if (u1 != 1) {
            throw new BusinessException("Fondos insuficientes o cuenta origen no encontrada");
        }

        int u2 = jdbc.update("""
            UPDATE cuentas
            SET saldo_billetera = saldo_billetera + ?,
                actualizado_en = NOW()
            WHERE curp = ?
        """, monto, destino);

        if (u2 != 1) {
            throw new BusinessException("Cuenta destino no encontrada");
        }
    }
}
