package dev.jta.demo.turmas;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TurmaService {

    private final TurmaRepository repository;

    public TurmaService(TurmaRepository repository) {
        this.repository = repository;
    }

    public List<Turma> listar() {
        return repository.findAll();
    }

    public Optional<Turma> buscar(String id) {
        return repository.findById(id);
    }

    public Turma criar(String nome, String ano) {
        Turma turma = new Turma(UUID.randomUUID().toString(), nome, ano);
        return repository.save(turma);
    }

    public void atualizar(String id, String nome, String ano) {
        repository.findById(id).ifPresent(turma -> {
            turma.setNome(nome);
            turma.setAno(ano);
            repository.save(turma);
        });
    }
}
