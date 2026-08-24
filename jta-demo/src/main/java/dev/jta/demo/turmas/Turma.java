package dev.jta.demo.turmas;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "turma")
public class Turma {

    @Id
    private String id;

    private String nome;
    private String ano;

    protected Turma() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Turma(String id, String nome, String ano) {
        this.id = id;
        this.nome = nome;
        this.ano = ano;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getAno() {
        return ano;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setAno(String ano) {
        this.ano = ano;
    }
}
