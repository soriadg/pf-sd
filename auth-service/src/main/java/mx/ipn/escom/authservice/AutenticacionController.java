package mx.ipn.escom.authservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AutenticacionController {

    private final AutenticacionServicio autenticacionServicio;

    public AutenticacionController(AutenticacionServicio autenticacionServicio) {
        this.autenticacionServicio = autenticacionServicio;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroRequest req) {
        autenticacionServicio.registrar(req.curp(), req.contrasena(), req.rol());
        return ResponseEntity.ok(Map.of("mensaje", "Usuario registrado correctamente"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String token = autenticacionServicio.login(req.curp(), req.contrasena());
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Protegido por SecurityConfig + JwtAuthFilter.
     * Si llega aquí, significa que el JWT fue válido.
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(Authentication authentication,
                                    @RequestHeader("Authorization") String authorization) {

        String token = authorization.substring("Bearer ".length()).trim();
        Object info = autenticacionServicio.verificarToken(token);

        return ResponseEntity.ok(Map.of(
                "auth", "OK",
                "principal", authentication.getPrincipal(),
                "info", info
        ));
    }

    // ===================== DTOs =====================
    public record RegistroRequest(
            @NotBlank(message = "curp es obligatoria")
            @Pattern(
                    regexp = "^[A-Z0-9]{18}$",
                    message = "curp debe tener 18 caracteres en mayúsculas (A-Z/0-9)"
            )
            String curp,

            @NotBlank(message = "contrasena es obligatoria")
            String contrasena,

            String rol
    ) {}

    public record LoginRequest(
            @NotBlank(message = "curp es obligatoria")
            @Pattern(
                    regexp = "^[A-Z0-9]{18}$",
                    message = "curp debe tener 18 caracteres en mayúsculas (A-Z/0-9)"
            )
            String curp,

            @NotBlank(message = "contrasena es obligatoria")
            String contrasena
    ) {}
}
