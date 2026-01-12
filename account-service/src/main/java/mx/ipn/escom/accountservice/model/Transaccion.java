package mx.ipn.escom.accountservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaccion {
    private UUID txId;
    private TipoTransaccion tipo;
    private String deCurp;
    private String aCurp;
    private BigDecimal monto;
    private StatusTransaccion status;
    private String errorReason;
    private Instant createdAt;

    public Transaccion(UUID txId, TipoTransaccion tipo, String deCurp, String aCurp,
                       BigDecimal monto, StatusTransaccion status, String errorReason, Instant createdAt) {
        this.txId = txId;
        this.tipo = tipo;
        this.deCurp = deCurp;
        this.aCurp = aCurp;
        this.monto = monto;
        this.status = status;
        this.errorReason = errorReason;
        this.createdAt = createdAt;
    }

    public UUID getTxId() { return txId; }
    public TipoTransaccion getTipo() { return tipo; }
    public String getDeCurp() { return deCurp; }
    public String getACurp() { return aCurp; }
    public BigDecimal getMonto() { return monto; }
    public StatusTransaccion getStatus() { return status; }
    public String getErrorReason() { return errorReason; }
    public Instant getCreatedAt() { return createdAt; }
}