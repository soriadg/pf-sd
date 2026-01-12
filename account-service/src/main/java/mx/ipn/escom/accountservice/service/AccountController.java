package mx.ipn.escom.accountservice.service;

import jakarta.validation.Valid;
import mx.ipn.escom.accountservice.error.BadRequestException;
import mx.ipn.escom.accountservice.error.NotFoundException;
import mx.ipn.escom.accountservice.error.UnauthorizedException;
import mx.ipn.escom.accountservice.dto.DepositRequest;
import mx.ipn.escom.accountservice.dto.TransferRequest;
import mx.ipn.escom.accountservice.dto.WithdrawRequest;
import mx.ipn.escom.accountservice.repo.TransaccionRepository;
import mx.ipn.escom.accountservice.security.JwtServicio;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/account")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService service;
    private final JdbcTemplate jdbcTemplate;
    private final TransaccionRepository txRepo;
    private final JwtServicio jwtServicio;

    public AccountController(AccountService service, JdbcTemplate jdbcTemplate, TransaccionRepository txRepo, JwtServicio jwtServicio) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.txRepo = txRepo;
        this.jwtServicio = jwtServicio;
    }

    @GetMapping("/balance")
    public ResponseEntity<?> balance(@RequestHeader(value = "Authorization", required = false) String auth) {
        // In production, extract CURP from JWT token
        // For now, we'll return error if no auth
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        // Extract CURP from JWT (simplified - in production use proper JWT validation)
        String curp = extractCurpFromAuth(auth);

        Map<String, Object> result;
        try {
            result = jdbcTemplate.queryForMap(
                "SELECT curp, saldo_banco, saldo_billetera FROM cuentas WHERE curp = ?", curp);
        } catch (EmptyResultDataAccessException ex) {
            throw new NotFoundException("La cuenta no existe: " + curp);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody Map<String, Double> body) {

        String curp = extractCurpFromAuth(auth);
        double monto = body.get("monto");

        service.deposit(curp, java.math.BigDecimal.valueOf(monto));
        return ResponseEntity.ok(Map.of("mensaje", "Depósito realizado"));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody Map<String, Double> body) {

        String curp = extractCurpFromAuth(auth);
        double monto = body.get("monto");

        service.withdraw(curp, java.math.BigDecimal.valueOf(monto));
        return ResponseEntity.ok(Map.of("mensaje", "Retiro realizado"));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @Valid @RequestBody Map<String, Object> body) throws Exception {

        String curpOrigen = extractCurpFromAuth(auth);
        String curpDestino = (String) body.get("curpDestino");
        Object montoObj = body.get("monto");
        
        if (montoObj == null) {
            throw new BadRequestException("El campo 'monto' es requerido");
        }
        
        double monto = ((Number) montoObj).doubleValue();

        UUID txId = service.transfer(curpOrigen, curpDestino, java.math.BigDecimal.valueOf(monto));
        return ResponseEntity.ok(Map.of("txId", txId.toString(), "status", "PENDIENTE"));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@RequestHeader(value = "Authorization", required = false) String auth) {
        String curp = extractCurpFromAuth(auth);

        String sql = "SELECT event_id, curp_origen, curp_destino, monto, tipo, estado, creado_en " +
                     "FROM transacciones " +
                     "WHERE curp_origen = ? OR curp_destino = ? " +
                     "ORDER BY creado_en DESC LIMIT 50";

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(sql, curp, curp);
        return ResponseEntity.ok(transactions);
    }

    // Simplified CURP extraction - in production, validate JWT properly
    private String extractCurpFromAuth(String auth) {
        if (auth == null || auth.isBlank()) {
            throw new UnauthorizedException("No autorizado");
        }

        if (!auth.startsWith("Bearer ")) {
            throw new BadRequestException("Authorization debe ser 'Bearer <token>'");
        }

        String token = auth.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Token vacío");
        }

        try {
            Claims claims = jwtServicio.validarYObtenerClaims(token);
            String curp = claims.getSubject();
            if (curp == null || curp.isBlank()) {
                throw new UnauthorizedException("Token inválido");
            }
            return curp;
        } catch (JwtException ex) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
    }
}
