package mx.ipn.escom.transactionservice;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Component
public class TransactionProcessor {

    private final TransactionTemplate txTemplate;
    private final TransactionRepository txRepo;
    private final AccountRepository accountRepo;
    private final AuditRepository auditRepo;

    public TransactionProcessor(PlatformTransactionManager txManager,
                                TransactionRepository txRepo,
                                AccountRepository accountRepo,
                                AuditRepository auditRepo) {
        this.txTemplate = new TransactionTemplate(txManager);
        this.txRepo = txRepo;
        this.accountRepo = accountRepo;
        this.auditRepo = auditRepo;
    }

    public TxRow procesarEnBD(String eventId, TxType tipo, String curpOrigen, String curpDestino, BigDecimal monto, String payloadOriginal) {
        return txTemplate.execute(status -> {
            // 0) Insert si no existe (idempotencia)
            txRepo.insertIfAbsent(eventId, tipo, curpOrigen, curpDestino, monto);

            // 1) Lock transacción
            TxRow row = txRepo.lockByEventId(eventId);
            if (row == null) throw new IllegalStateException("No existe transacción en BD para event_id=" + eventId);

            // 2) Si ya fue CONFIRMADA, NO tocar balances (idempotente)
            if ("CONFIRMADA".equalsIgnoreCase(row.estado())) {
                // Nota: no insertamos auditoría otra vez para evitar duplicados
                return row;
            }

            // 3) Si ya fue FALLIDA, tampoco tocar balances
            if ("FALLIDA".equalsIgnoreCase(row.estado())) {
                return row;
            }

            // 4) Aplicar operación (si falla por negocio → marcar FALLIDA y COMMIT)
            try {
                aplicarOperacion(row);
                txRepo.markConfirmed(eventId);
                auditRepo.insertar(row.id(), "TRANSACCION_CONFIRMADA", payloadOriginal);
                return new TxRow(row.id(), row.eventId(), row.tipo(), row.curpOrigen(), row.curpDestino(), row.monto(), "CONFIRMADA");
            } catch (BusinessException be) {
                txRepo.markFailed(eventId);
                auditRepo.insertar(row.id(), "TRANSACCION_FALLIDA", payloadOriginal);
                return new TxRow(row.id(), row.eventId(), row.tipo(), row.curpOrigen(), row.curpDestino(), row.monto(), "FALLIDA");
            }
        });
    }

    private void aplicarOperacion(TxRow row) {
        switch (row.tipo()) {
            case DEPOSITO -> {
                // BD exige curp_destino != null, curp_origen = null
                String usuario = row.curpDestino();
                if (usuario == null || usuario.isBlank()) {
                    throw new BusinessException("DEPOSITO requiere curp_destino (usuario).");
                }
                accountRepo.deposito(usuario, row.monto());
            }
            case RETIRO -> {
                // BD exige curp_origen != null, curp_destino = null
                String usuario = row.curpOrigen();
                if (usuario == null || usuario.isBlank()) {
                    throw new BusinessException("RETIRO requiere curp_origen (usuario).");
                }
                accountRepo.retiro(usuario, row.monto());
            }
            case TRANSFERENCIA -> accountRepo.transferencia(row.curpOrigen(), row.curpDestino(), row.monto());
        }
    }
}
