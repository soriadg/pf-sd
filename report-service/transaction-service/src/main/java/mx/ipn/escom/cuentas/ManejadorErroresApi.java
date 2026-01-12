package mx.ipn.escom.cuentas;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ManejadorErroresApi {

    @ExceptionHandler(ExcepcionHttp.class)
    public ResponseEntity<?> manejarExcepcionHttp(ExcepcionHttp ex) {
        return ResponseEntity.status(ex.getCodigo())
                .body(Map.of("error", ex.getMessage(), "codigo", ex.getCodigo()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> manejarValidacionBody(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "Validación fallida", "detalles", errores));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> manejarValidacionParams(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Validación fallida", "detalles", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarGeneral(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error", "Error interno", "detalle", ex.getMessage()));
    }
}
