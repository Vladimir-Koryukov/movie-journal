package dev.vladimir.moviejournal.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MovieCastMember {

    @Column(name = "actor_name", nullable = false, length = 200)
    private String name;

    protected MovieCastMember() {
    }

    public MovieCastMember(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException(
                    "Actor name must contain from 1 to 200 characters");
        }

        this.name = name.strip();
    }

    public String getName() {
        return name;
    }
}
