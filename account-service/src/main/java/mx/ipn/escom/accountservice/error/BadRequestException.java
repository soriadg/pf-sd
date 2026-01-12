package mx.ipn.escom.accountservice.error;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String msg) { super(msg); }
}