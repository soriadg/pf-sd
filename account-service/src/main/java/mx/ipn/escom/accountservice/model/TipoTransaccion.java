package mx.ipn.escom.accountservice.model;

public enum TipoTransaccion {
    DEPOSIT("DEPOSITO"),
    WITHDRAW("RETIRO"),
    TRANSFER("TRANSFERENCIA");

    private final String dbValue;
    TipoTransaccion(String dbValue) { this.dbValue = dbValue; }
    public String db() { return dbValue; }
}
