package mx.ipn.escom.cuentas;

public class ExcepcionHttp extends RuntimeException {
    private final int codigo;

    public ExcepcionHttp(int codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }
}
