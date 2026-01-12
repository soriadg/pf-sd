package mx.ipn.escom.accountservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Service
public class JwtServicio {

    private final Key llave;

    public JwtServicio(@Value("${jwt.secreto}") String secreto) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalArgumentException("jwt.secreto debe tener mínimo 32 caracteres (HS256).");
        }
        this.llave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validarYObtenerClaims(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(llave)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
