package mx.ipn.escom.accountservice.service;

import mx.ipn.escom.accountservice.error.BadRequestException;
import mx.ipn.escom.accountservice.error.InsufficientFundsException;
import mx.ipn.escom.accountservice.error.NotFoundException;
import mx.ipn.escom.accountservice.model.StatusTransaccion;
import mx.ipn.escom.accountservice.model.TipoTransaccion;
import mx.ipn.escom.accountservice.pubsub.TransferEvent;
import mx.ipn.escom.accountservice.pubsub.TransferPublisher;
import mx.ipn.escom.accountservice.repo.CuentaRepository;
import mx.ipn.escom.accountservice.repo.TransaccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {

    private final CuentaRepository cuentaRepo;
    private final TransaccionRepository txRepo;
    private final TransferPublisher publisher;

    public AccountService(CuentaRepository cuentaRepo, TransaccionRepository txRepo, TransferPublisher publisher) {
        this.cuentaRepo = cuentaRepo;
        this.txRepo = txRepo;
        this.publisher = publisher;
    }

    /** Regresa saldo TOTAL (billetera + banco). */
    public BigDecimal getBalance(String curp) {
        return cuentaRepo.obtenerSaldoTotal(curp)
                .orElseThrow(() -> new NotFoundException("La cuenta no existe: " + curp));
    }

    /** Depósito síncrono (por defecto a saldo_billetera). */
    @Transactional
    public void deposit(String curp, BigDecimal amount) {
        validarMonto(amount);

        if (!cuentaRepo.existeCuenta(curp)) {
            throw new NotFoundException("La cuenta no existe: " + curp);
        }

        BigDecimal actual = cuentaRepo.obtenerSaldoBilleteraForUpdate(curp);
        BigDecimal nuevo = actual.add(amount);

        cuentaRepo.actualizarSaldoBilletera(curp, nuevo);

        UUID txId = UUID.randomUUID();
        String eventId = txId.toString();

        txRepo.insertar(
                txId,
                eventId,
                TipoTransaccion.DEPOSIT,
                StatusTransaccion.CONFIRMED,
                null,         // curp_origen NULL para DEPOSITO
                curp,         // curp_destino NOT NULL
                amount,
                Instant.now() // confirmado_en obligatorio para CONFIRMADA
        );
    }

    /** Retiro síncrono (por defecto de saldo_billetera). */
    @Transactional
    public void withdraw(String curp, BigDecimal amount) {
        validarMonto(amount);

        if (!cuentaRepo.existeCuenta(curp)) {
            throw new NotFoundException("La cuenta no existe: " + curp);
        }

        BigDecimal actual = cuentaRepo.obtenerSaldoBilleteraForUpdate(curp);

        if (actual.compareTo(amount) < 0) {
            UUID txId = UUID.randomUUID();
            String eventId = txId.toString();

            txRepo.insertar(
                    txId,
                    eventId,
                    TipoTransaccion.WITHDRAW,
                    StatusTransaccion.REJECTED, // -> FALLIDA en BD
                    curp,   // origen NOT NULL para RETIRO
                    null,   // destino NULL
                    amount,
                    Instant.now()
            );

            throw new InsufficientFundsException("Fondos insuficientes");
        }

        BigDecimal nuevo = actual.subtract(amount);
        cuentaRepo.actualizarSaldoBilletera(curp, nuevo);
        
        // Actualizar saldo banco (añadir el monto retirado)
        BigDecimal saldoBancoActual = cuentaRepo.obtenerSaldoBancoForUpdate(curp);
        BigDecimal saldoBancoNuevo = saldoBancoActual.add(amount);
        cuentaRepo.actualizarSaldoBanco(curp, saldoBancoNuevo);

        UUID txId = UUID.randomUUID();
        String eventId = txId.toString();

        txRepo.insertar(
                txId,
                eventId,
                TipoTransaccion.WITHDRAW,
                StatusTransaccion.CONFIRMED,
                curp,
                null,
                amount,
                Instant.now()
        );
    }

    /**
     * Transferencia ASÍNCRONA:
     * - valida
     * - crea transacción PENDIENTE
     * - publica evento a Pub/Sub
     * - NO mueve saldo aquí
     */
    @Transactional
    public UUID transfer(String fromCurp, String toCurp, BigDecimal amount) throws Exception {
        validarMonto(amount);

        if (fromCurp == null || toCurp == null) {
            throw new BadRequestException("fromCurp y toCurp son obligatorios");
        }

        if (fromCurp.equalsIgnoreCase(toCurp)) {
            throw new BadRequestException("fromCurp y toCurp no pueden ser iguales");
        }

        if (!cuentaRepo.existeCuenta(fromCurp)) {
            throw new NotFoundException("No existe cuenta origen: " + fromCurp);
        }
        if (!cuentaRepo.existeCuenta(toCurp)) {
            throw new NotFoundException("No existe cuenta destino: " + toCurp);
        }

        // pre-check en billetera (TransactionService revalida de todos modos)
        BigDecimal actual = cuentaRepo.obtenerSaldoBilleteraForUpdate(fromCurp);
        if (actual.compareTo(amount) < 0) {
            UUID txId = UUID.randomUUID();
            String eventId = txId.toString();

            txRepo.insertar(
                    txId,
                    eventId,
                    TipoTransaccion.TRANSFER,
                    StatusTransaccion.REJECTED, // -> FALLIDA
                    fromCurp,
                    toCurp,
                    amount,
                    Instant.now()
            );

            throw new InsufficientFundsException("Fondos insuficientes");
        }

        UUID txId = UUID.randomUUID();
        String eventId = txId.toString();

        // PENDIENTE => confirmado_en debe ser NULL
        txRepo.insertar(
                txId,
                eventId,
                TipoTransaccion.TRANSFER,
                StatusTransaccion.PENDING,
                fromCurp,
                toCurp,
                amount,
                null
        );

        // ✅ TransferEvent tiene 6 campos: NO metas eventId aquí
        TransferEvent event = new TransferEvent(
                txId.toString(),
                fromCurp,
                toCurp,
                amount,
                Instant.now().toEpochMilli(),
                "TRANSFER" // atributo type (ajústalo si tu subscriber espera otro)
        );

        publisher.publish(event);

        return txId;
    }

    private void validarMonto(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("El monto debe ser mayor que 0");
        }
    }
}
