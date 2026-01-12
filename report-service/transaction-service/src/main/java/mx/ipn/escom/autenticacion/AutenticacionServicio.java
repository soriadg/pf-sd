package mx.ipn.escom.autenticacion;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.Map;

@Service
public class AutenticacionServicio {

    private final JwtServicio jwtServicio;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPass;

    public AutenticacionServicio(JwtServicio jwtServicio) {
        this.jwtServicio = jwtServicio;
    }

    // ✅ REGISTRO
    public void registrar(String curp, String contrasena, String rol) {
        if (curp == null || curp.isBlank()) throw new IllegalArgumentException("CURP requerida");
        if (contrasena == null || contrasena.isBlank()) throw new IllegalArgumentException("Contraseña requerida");

        String rolFinal = (rol == null || rol.isBlank()) ? "USUARIO" : rol.toUpperCase();

        try (Connection conn = obtenerConexion()) {

            // Verificar si ya existe
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

        try (Connection conn = obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT hash_contrasena, rol FROM usuarios WHERE curp = ?"
             )) {

            ps.setString(1, curp.trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new IllegalArgumentException("Credenciales inválidas");
            }

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

    // ✅ VERIFY (solo para prueba)
    public Map<String, Object> verificarToken(String token) {
        Claims claims = jwtServicio.validarYObtenerClaims(token);
        return Map.of(
                "curp", claims.getSubject(),
                "rol", claims.get("rol"),
                "expira", claims.getExpiration()
        );
    }

    // ===================== JDBC =====================
    private Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }
}
