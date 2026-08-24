package dev.jta.demo.notas;

import dev.jta.demo.alunos.Aluno;
import dev.jta.demo.disciplinas.Disciplina;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "nota")
public class Nota {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    private double valor;

    protected Nota() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Nota(String id, Aluno aluno, Disciplina disciplina, double valor) {
        this.id = id;
        this.aluno = aluno;
        this.disciplina = disciplina;
        this.valor = valor;
    }

    public String getId() {
        return id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public Disciplina getDisciplina() {
        return disciplina;
    }

    public double getValor() {
        return valor;
    }
}
