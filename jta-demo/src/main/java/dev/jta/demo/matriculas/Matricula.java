package dev.jta.demo.matriculas;

import dev.jta.demo.alunos.Aluno;
import dev.jta.demo.turmas.Turma;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Liga um aluno a uma turma - entidade de juncao, mesmo papel que Visita cumpria no demo anterior (Pet x data). */
@Entity
@Table(name = "matricula")
public class Matricula {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    protected Matricula() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Matricula(String id, Aluno aluno, Turma turma) {
        this.id = id;
        this.aluno = aluno;
        this.turma = turma;
    }

    public String getId() {
        return id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public Turma getTurma() {
        return turma;
    }
}
