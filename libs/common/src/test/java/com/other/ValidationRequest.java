package com.other;

import jakarta.validation.constraints.NotBlank;

public record ValidationRequest(@NotBlank String name) {
}
