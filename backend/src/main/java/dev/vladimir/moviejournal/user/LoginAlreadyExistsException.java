package dev.vladimir.moviejournal.user;

public class LoginAlreadyExistsException extends RuntimeException {

    public LoginAlreadyExistsException() {
        super("Это имя пользователя уже занято");
    }
}
