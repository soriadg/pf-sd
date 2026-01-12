package mx.ipn.escom.autenticacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AutenticacionController {

    private final AutenticacionServicio autenticacionServicio;

    public AutenticacionController(AutenticacionServicio autenticacionServicio) {
        this.autenticacionServicio = autenticacionServicio;
    }

    // ✅ Registro: CURP + contraseña + rol opcional (USUARIO/ADMIN)
    @PostMapping("/register")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroRequest req) {
        autenticacionServicio.registrar(req.curp(), req.contrasena(), req.rol());
        return ResponseEntity.ok(Map.of("mensaje", "Usuario registrado correctamente"));
    }

    // ✅ Login: CURP + contraseña → JWT
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String token = autenticacionServicio.login(req.curp(), req.contrasena());
        return ResponseEntity.ok(Map.of("token", token));
    }

    // ✅ Verify: valida token (útil para debug/profe)
    @GetMapping("/verify")
    public ResponseEntity<?> verificar(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Falta el header Authorization: Bearer <token>"));
        }

        String token = authorization.replace("Bearer", "").trim();
        if (token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authorization inválido. Usa: Bearer <token>"));
        }

        Object info = autenticacionServicio.verificarToken(token);
        return ResponseEntity.ok(info);
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

            // opcional: si no viene, el servicio puede default a USUARIO
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
