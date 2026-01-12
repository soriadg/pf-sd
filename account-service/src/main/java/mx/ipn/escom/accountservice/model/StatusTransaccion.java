package mx.ipn.escom.accountservice.model;

public enum StatusTransaccion {
    PENDING("PENDIENTE"),
    CONFIRMED("CONFIRMADA"),
    REJECTED("FALLIDA"); // REJECTED = FALLIDA en BD

    private final String dbValue;
    StatusTransaccion(String dbValue) { this.dbValue = dbValue; }
    public String db() { return dbValue; }
}
