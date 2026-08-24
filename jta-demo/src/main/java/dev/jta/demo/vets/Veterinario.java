package dev.jta.demo.vets;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA (analoga a {@code Vet} do Spring PetClinic). Cadastro/edicao
 * sao as duas unicas paginas do demo protegidas por
 * {@code @RequiresRole("ADMIN")} - ver {@code VeterinarioNovo}/
 * {@code VeterinarioEditar} - primeiro dogfood real da anotacao num app
 * rodando de verdade (antes so era exercitada em compile-time por
 * {@code scripts/smoke-test.sh}).
 */
@Entity
@Table(name = "veterinario")
public class Veterinario {

    @Id
    private String id;

    private String nome;
    private String especialidade;

    protected Veterinario() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Veterinario(String id, String nome, String especialidade) {
        this.id = id;
        this.nome = nome;
        this.especialidade = especialidade;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
    }
}
