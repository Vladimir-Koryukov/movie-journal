package dev.vladimir.moviejournal.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    protected Genre() {
    }

    public Genre(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException(
                    "Genre name must contain from 1 to 100 characters");
        }

        this.name = name.strip();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Genre genre)) {
            return false;
        }
        return name.equalsIgnoreCase(genre.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(Locale.ROOT));
    }
}
