package mx.ipn.escom.transactionservice;

public enum TxType {
    DEPOSITO,
    RETIRO,
    TRANSFERENCIA;

    public static TxType from(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Tipo de transacción null");
        }

        return switch (s.trim().toUpperCase()) {
            case "DEPOSITO", "DEPOSIT" -> DEPOSITO;
            case "RETIRO", "WITHDRAW" -> RETIRO;
            case "TRANSFER", "TRANSFERENCIA" -> TRANSFERENCIA;
            default -> throw new IllegalArgumentException("Tipo de transacción inválido: " + s);
        };
    }
}
