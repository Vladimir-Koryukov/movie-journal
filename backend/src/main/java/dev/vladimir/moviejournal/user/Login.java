package dev.vladimir.moviejournal.user;

import java.util.Locale;

public record Login(String value) {

    public Login {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Укажите имя пользователя");
        }

        value = value.strip().toLowerCase(Locale.ROOT);

        if (!value.matches("[a-z0-9._-]{3,64}")) {
            throw new IllegalArgumentException(
                    "Имя пользователя должно содержать от 3 до 64 "
                            + "латинских букв, цифр и символов . _ -");
        }
    }
}
