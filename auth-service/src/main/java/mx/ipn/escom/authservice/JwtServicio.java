package mx.ipn.escom.authservice;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtServicio {

    private final Key llave;
    private final long expiracionSegundos;

    public JwtServicio(
            @Value("${jwt.secreto}") String secreto,
            @Value("${jwt.expiracion-segundos:3600}") long expiracionSegundos
    ) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalArgumentException("jwt.secreto debe tener mínimo 32 caracteres (HS256).");
        }
        this.llave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionSegundos = expiracionSegundos;
    }

    public String generarToken(String curp, String rol) {
        Instant ahora = Instant.now();
        Instant exp = ahora.plusSeconds(expiracionSegundos);

        return Jwts.builder()
                .setSubject(curp)
                .addClaims(Map.of("rol", rol))
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(exp))
                .signWith(llave, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validarYObtenerClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(llave)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
