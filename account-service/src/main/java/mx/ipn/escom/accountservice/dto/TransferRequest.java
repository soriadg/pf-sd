package mx.ipn.escom.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank String fromCurp,
        @NotBlank String toCurp,
        @NotNull @Positive BigDecimal amount
) {}