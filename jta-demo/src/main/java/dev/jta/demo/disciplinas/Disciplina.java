package dev.jta.demo.disciplinas;

import dev.jta.demo.professores.Professor;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Uma disciplina tem um unico professor responsavel - primeira relacao muitos-para-um do demo. */
@Entity
@Table(name = "disciplina")
public class Disciplina {

    @Id
    private String id;

    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    protected Disciplina() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Disciplina(String id, String nome, Professor professor) {
        this.id = id;
        this.nome = nome;
        this.professor = professor;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Professor getProfessor() {
        return professor;
    }
}
