package dev.jta.demo.tutores;

import dev.jta.demo.pets.Pet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidade JPA (analoga a {@code Owner} do Spring PetClinic). Um tutor tem
 * varios pets - {@code cascade = ALL, orphanRemoval = true} garante que
 * excluir um tutor tambem exclui seus pets (e, em cascata, as visitas de
 * cada pet - ver {@link Pet#getVisitas()}), evitando violacao de FK ao
 * excluir.
 */
@Entity
@Table(name = "tutor")
public class Tutor {

    @Id
    private String id;

    private String nome;
    private String telefone;
    private String endereco;

    @OneToMany(mappedBy = "tutor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pet> pets = new ArrayList<>();

    protected Tutor() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Tutor(String id, String nome, String telefone, String endereco) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getEndereco() {
        return endereco;
    }

    public List<Pet> getPets() {
        return pets;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }
}
