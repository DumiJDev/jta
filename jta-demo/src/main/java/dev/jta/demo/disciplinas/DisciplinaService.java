package dev.jta.demo.disciplinas;

import dev.jta.demo.professores.Professor;
import dev.jta.demo.professores.ProfessorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DisciplinaService {

    private final DisciplinaRepository repository;
    private final ProfessorRepository professorRepository;

    public DisciplinaService(DisciplinaRepository repository, ProfessorRepository professorRepository) {
        this.repository = repository;
        this.professorRepository = professorRepository;
    }

    public List<Disciplina> listar() {
        return repository.findAll();
    }

    public Optional<Disciplina> buscar(String id) {
        return repository.findById(id);
    }

    public Disciplina criar(String nome, String professorId) {
        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new IllegalArgumentException("Professor '" + professorId + "' nao encontrado"));
        Disciplina disciplina = new Disciplina(UUID.randomUUID().toString(), nome, professor);
        return repository.save(disciplina);
    }
}
