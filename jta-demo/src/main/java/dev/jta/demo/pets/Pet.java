package dev.jta.demo.pets;

import dev.jta.demo.tutores.Tutor;
import dev.jta.demo.visitas.Visita;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade JPA (analoga a {@code Pet} do Spring PetClinic) - pertence a
 * um {@link Tutor} e tem varias {@link Visita}s, ambas em cascata (ver
 * {@link Tutor#getPets()}).
 */
@Entity
@Table(name = "pet")
public class Pet {

    @Id
    private String id;

    private String nome;
    private String especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id")
    private Tutor tutor;

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Visita> visitas = new ArrayList<>();

    protected Pet() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Pet(String id, String nome, String especie, Tutor tutor) {
        this.id = id;
        this.nome = nome;
        this.especie = especie;
        this.tutor = tutor;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEspecie() {
        return especie;
    }

    public Tutor getTutor() {
        return tutor;
    }

    public List<Visita> getVisitas() {
        return visitas;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }
}
