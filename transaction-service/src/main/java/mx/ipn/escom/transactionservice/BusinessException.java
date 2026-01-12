package mx.ipn.escom.transactionservice;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
