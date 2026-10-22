package dev.vladimir.moviejournal.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Укажите имя пользователя")
        @Size(
                max = 64,
                message = "Имя пользователя не должно быть длиннее "
                        + "64 символов"
        )
        String login,

        @NotBlank(message = "Укажите пароль")
        @Size(
                min = 12,
                max = 72,
                message = "Пароль должен содержать от 12 до 72 символов"
        )
        String password
) {
    @Override
    public String toString() {
        return "RegisterUserRequest[credentials=REDACTED]";
    }
}
