package dev.jta.demo.notas;

import dev.jta.demo.alunos.Aluno;
import dev.jta.demo.alunos.AlunoRepository;
import dev.jta.demo.disciplinas.Disciplina;
import dev.jta.demo.disciplinas.DisciplinaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotaService {

    private final NotaRepository repository;
    private final AlunoRepository alunoRepository;
    private final DisciplinaRepository disciplinaRepository;

    public NotaService(NotaRepository repository, AlunoRepository alunoRepository,
                        DisciplinaRepository disciplinaRepository) {
        this.repository = repository;
        this.alunoRepository = alunoRepository;
        this.disciplinaRepository = disciplinaRepository;
    }

    public List<Nota> porAluno(String alunoId) {
        return repository.findByAlunoId(alunoId);
    }

    public Nota lancar(String alunoId, String disciplinaId, double valor) {
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new IllegalArgumentException("Aluno '" + alunoId + "' nao encontrado"));
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina '" + disciplinaId + "' nao encontrada"));
        Nota nota = new Nota(UUID.randomUUID().toString(), aluno, disciplina, valor);
        return repository.save(nota);
    }
}
