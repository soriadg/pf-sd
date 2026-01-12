package mx.ipn.escom.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record DepositRequest(
        @NotBlank String curp,
        @NotNull @Positive BigDecimal amount
) {}