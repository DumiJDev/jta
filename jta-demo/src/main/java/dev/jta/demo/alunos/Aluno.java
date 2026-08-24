package dev.jta.demo.alunos;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "aluno")
public class Aluno {

    @Id
    private String id;

    private String nome;
    private String email;
    private String nascimento;

    protected Aluno() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Aluno(String id, String nome, String email, String nascimento) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.nascimento = nascimento;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getNascimento() {
        return nascimento;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNascimento(String nascimento) {
        this.nascimento = nascimento;
    }
}
