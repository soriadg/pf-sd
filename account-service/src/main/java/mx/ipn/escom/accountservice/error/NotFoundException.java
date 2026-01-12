package mx.ipn.escom.accountservice.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String msg) { super(msg); }
}