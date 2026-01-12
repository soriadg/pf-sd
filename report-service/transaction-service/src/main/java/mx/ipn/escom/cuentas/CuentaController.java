package mx.ipn.escom.cuentas;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import mx.ipn.escom.autenticacion.JwtServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/account")
@Validated
public class CuentaController {

    private final CuentaServicio cuentaServicio;
    private final JwtServicio jwtServicio;

    public CuentaController(CuentaServicio cuentaServicio, JwtServicio jwtServicio) {
        this.cuentaServicio = cuentaServicio;
        this.jwtServicio = jwtServicio;
    }

    // =========================
    // GET /account/balance
    // =========================
    @GetMapping("/balance")
    public ResponseEntity<?> balance(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String curp = extraerCurp(authorization);
        Map<String, Object> resp = cuentaServicio.obtenerBalance(curp);
        return ResponseEntity.ok(resp);
    }

    // =========================
    // POST /account/deposit
    // =========================
    @PostMapping("/deposit")
    public ResponseEntity<?> depositar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DepositoRequest req
    ) {
        String curp = extraerCurp(authorization);
        Map<String, Object> resp = cuentaServicio.depositar(curp, req.monto());
        return ResponseEntity.ok(resp);
    }

    // =========================
    // POST /account/withdraw
    // =========================
    @PostMapping("/withdraw")
    public ResponseEntity<?> retirar(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RetiroRequest req
    ) {
        String curp = extraerCurp(authorization);
        Map<String, Object> resp = cuentaServicio.retirar(curp, req.monto());
        return ResponseEntity.ok(resp);
    }

    // =========================
    // POST /account/transfer
    // =========================
    @PostMapping("/transfer")
    public ResponseEntity<?> transferir(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TransferenciaRequest req
    ) {
        String curpOrigen = extraerCurp(authorization);
        Map<String, Object> resp = cuentaServicio.transferir(curpOrigen, req.curpDestino(), req.monto());
        return ResponseEntity.ok(resp);
    }

    // (Opcional) consultar estado por event_id
    @GetMapping("/transaction/{eventId}")
    public ResponseEntity<?> estadoTransaccion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String eventId
    ) {
        extraerCurp(authorization); // solo valida token
        Map<String, Object> resp = cuentaServicio.consultarEstado(eventId);
        return ResponseEntity.ok(resp);
    }

    // =========================
    // Helper token -> CURP
    // =========================
    private String extraerCurp(String authorization) {
        if (authorization == null || authorization.isBlank() || !authorization.startsWith("Bearer ")) {
            throw new ExcepcionHttp(401, "Falta Authorization: Bearer <token>");
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new ExcepcionHttp(401, "Token vacío. Usa: Bearer <token>");
        }

        try {
            Claims claims = jwtServicio.validarYObtenerClaims(token);
            String curp = claims.getSubject();
            if (curp == null || curp.isBlank()) {
                throw new ExcepcionHttp(401, "Token inválido: subject (CURP) vacío");
            }
            return curp;
        } catch (Exception e) {
            throw new ExcepcionHttp(401, "Token inválido o expirado");
        }
    }

    // =========================
    // DTOs (validación)
    // =========================
    public record DepositoRequest(
            @Positive(message = "monto debe ser mayor a 0")
            double monto
    ) {}

    public record RetiroRequest(
            @Positive(message = "monto debe ser mayor a 0")
            double monto
    ) {}

    public record TransferenciaRequest(
            @NotBlank(message = "curpDestino es obligatorio")
            @Pattern(
                    regexp = "^[A-Z0-9]{18}$",
                    message = "curpDestino debe tener 18 caracteres en mayúsculas (A-Z/0-9)"
            )
            String curpDestino,

            @Positive(message = "monto debe ser mayor a 0")
            double monto
    ) {}
}
