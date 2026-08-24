package dev.jta.demo.visitas;

import dev.jta.demo.pets.Pet;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidade JPA (analoga a {@code Visit} do Spring PetClinic) - pertence a
 * um {@link Pet}. Sem tela propria: so criada via a acao
 * {@code registrarVisita()} de {@code PetDetalhe}, e persistida em
 * cascata a partir de {@link Pet#getVisitas()} (ver {@code PetService}).
 * {@code data} e {@code String} (nao {@code LocalDate}) de proposito - o
 * conjunto de tipos que {@code ComponentInvoker} sabe reidratar a partir
 * de parametros de requisicao e limitado a
 * {@code String}/{@code int}/{@code long}/{@code double}/{@code boolean}
 * (limitacao conhecida e documentada), entao o componente trafega a data
 * como texto (formato {@code aaaa-mm-dd}, o que {@code <input type="date">}
 * ja envia nativamente).
 */
@Entity
@Table(name = "visita")
public class Visita {

    @Id
    private String id;

    private String data;
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    protected Visita() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Visita(String id, String data, String descricao, Pet pet) {
        this.id = id;
        this.data = data;
        this.descricao = descricao;
        this.pet = pet;
    }

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public Pet getPet() {
        return pet;
    }
}
