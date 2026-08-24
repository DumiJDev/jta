package dev.jta.demo.professores;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfessorService {

    private final ProfessorRepository repository;

    public ProfessorService(ProfessorRepository repository) {
        this.repository = repository;
    }

    public List<Professor> listar() {
        return repository.findAll();
    }

    public Optional<Professor> buscar(String id) {
        return repository.findById(id);
    }

    public Professor criar(String nome, String especialidade) {
        Professor professor = new Professor(UUID.randomUUID().toString(), nome, especialidade);
        return repository.save(professor);
    }
}
