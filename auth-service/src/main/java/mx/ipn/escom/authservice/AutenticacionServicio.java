package mx.ipn.escom.authservice;

import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;

@Service
public class AutenticacionServicio {

    private final JwtServicio jwtServicio;
    private final PasswordEncoder encoder;
    private final DataSource dataSource;

    public AutenticacionServicio(JwtServicio jwtServicio, PasswordEncoder encoder, DataSource dataSource) {
        this.jwtServicio = jwtServicio;
        this.encoder = encoder;
        this.dataSource = dataSource;
    }

    // ✅ REGISTRO
    public void registrar(String curp, String contrasena, String rol) {
        if (curp == null || curp.isBlank()) throw new IllegalArgumentException("CURP requerida");
        if (contrasena == null || contrasena.isBlank()) throw new IllegalArgumentException("Contraseña requerida");

        String rolFinal = (rol == null || rol.isBlank()) ? "USUARIO" : rol.toUpperCase().trim();
        if (!rolFinal.equals("USUARIO") && !rolFinal.equals("ADMIN")) {
            throw new IllegalArgumentException("ROL inválido. Usa USUARIO o ADMIN");
        }

        try (Connection conn = dataSource.getConnection()) {
            // Verifica si ya existe
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM usuarios WHERE curp = ?"
            )) {
                check.setString(1, curp.trim());
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    throw new IllegalStateException("El usuario ya existe");
                }
            }

            String hash = encoder.encode(contrasena);

            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO usuarios (curp, hash_contrasena, rol) VALUES (?, ?, ?)"
            )) {
                ins.setString(1, curp.trim());
                ins.setString(2, hash);
                ins.setString(3, rolFinal);
                ins.executeUpdate();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error registrando usuario: " + e.getMessage(), e);
        }
    }

    // ✅ LOGIN
    public String login(String curp, String contrasena) {
        if (curp == null || curp.isBlank()) throw new IllegalArgumentException("CURP requerida");
        if (contrasena == null || contrasena.isBlank()) throw new IllegalArgumentException("Contraseña requerida");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT hash_contrasena, rol FROM usuarios WHERE curp = ?"
             )) {

            ps.setString(1, curp.trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) throw new IllegalArgumentException("Credenciales inválidas");

            String hash = rs.getString("hash_contrasena");
            String rol = rs.getString("rol");

            if (!encoder.matches(contrasena, hash)) {
                throw new IllegalArgumentException("Credenciales inválidas");
            }

            return jwtServicio.generarToken(curp.trim(), rol);

        } catch (SQLException e) {
            throw new RuntimeException("Error en login: " + e.getMessage(), e);
        }
    }

    // ✅ VERIFY
    public Map<String, Object> verificarToken(String token) {
        Claims claims = jwtServicio.validarYObtenerClaims(token);
        return Map.of(
                "curp", claims.getSubject(),
                "rol", claims.get("rol"),
                "expira", claims.getExpiration()
        );
    }
}
