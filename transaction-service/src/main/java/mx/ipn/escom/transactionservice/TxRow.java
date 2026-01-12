package mx.ipn.escom.transactionservice;

import java.math.BigDecimal;
import java.util.UUID;

public record TxRow(
        UUID id,
        String eventId,
        TxType tipo,
        String curpOrigen,
        String curpDestino,
        BigDecimal monto,
        String estado
) {}
