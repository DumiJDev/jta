package dev.jta.demo.matriculas;

import dev.jta.demo.alunos.Aluno;
import dev.jta.demo.alunos.AlunoRepository;
import dev.jta.demo.turmas.Turma;
import dev.jta.demo.turmas.TurmaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MatriculaService {

    private final MatriculaRepository repository;
    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;

    public MatriculaService(MatriculaRepository repository, AlunoRepository alunoRepository,
                             TurmaRepository turmaRepository) {
        this.repository = repository;
        this.alunoRepository = alunoRepository;
        this.turmaRepository = turmaRepository;
    }

    public List<Matricula> porAluno(String alunoId) {
        return repository.findByAlunoId(alunoId);
    }

    public Matricula matricular(String alunoId, String turmaId) {
        Aluno aluno = alunoRepository.findById(alunoId)
                .orElseThrow(() -> new IllegalArgumentException("Aluno '" + alunoId + "' nao encontrado"));
        Turma turma = turmaRepository.findById(turmaId)
                .orElseThrow(() -> new IllegalArgumentException("Turma '" + turmaId + "' nao encontrada"));
        Matricula matricula = new Matricula(UUID.randomUUID().toString(), aluno, turma);
        return repository.save(matricula);
    }

    /** Usado pelo widget {@code @Sse} da home - total de matriculas ativas no sistema. */
    public long total() {
        return repository.count();
    }
}
