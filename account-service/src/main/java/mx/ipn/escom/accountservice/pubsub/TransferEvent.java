package mx.ipn.escom.accountservice.pubsub;

import java.math.BigDecimal;

public record TransferEvent(
        String txId,
        String fromCurp,
        String toCurp,
        BigDecimal amount,
        long timestampEpochMs,
        String type
) {}
