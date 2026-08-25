package dev.jta.demo.alunos;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlunoService {

    private final AlunoRepository repository;

    public AlunoService(AlunoRepository repository) {
        this.repository = repository;
    }

    /** {@code q} vazio devolve todos - usado pela busca ao vivo de {@code AlunoLista}. */
    public List<Aluno> listar(String q) {
        return q == null || q.isBlank()
                ? repository.findAll()
                : repository.findByNomeContainingIgnoreCaseOrderByNome(q);
    }

    public Optional<Aluno> buscar(String id) {
        return repository.findById(id);
    }

    public Aluno criar(String nome, String email, String nascimento) {
        Aluno aluno = new Aluno(UUID.randomUUID().toString(), nome, email, nascimento);
        return repository.save(aluno);
    }

    public void atualizar(String id, String nome, String email, String nascimento) {
        repository.findById(id).ifPresent(aluno -> {
            aluno.setNome(nome);
            aluno.setEmail(email);
            aluno.setNascimento(nascimento);
            repository.save(aluno);
        });
    }

    public void excluir(String id) {
        repository.deleteById(id);
    }
}
