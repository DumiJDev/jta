package dev.jta.demo.tutores;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servico Spring comum - so mais um bean que qualquer componente JTA pode
 * receber via construtor (ver {@code TutorLista}/{@code TutorDetalhe}).
 */
@Service
public class TutorService {

    private final TutorRepository repository;

    public TutorService(TutorRepository repository) {
        this.repository = repository;
    }

    public List<Tutor> listar() {
        return repository.findAll();
    }

    public Optional<Tutor> buscar(String id) {
        return repository.findById(id);
    }

    public Tutor criar(String nome, String telefone, String endereco) {
        Tutor tutor = new Tutor(UUID.randomUUID().toString(), nome, telefone, endereco);
        return repository.save(tutor);
    }

    public void atualizar(String id, String nome, String telefone, String endereco) {
        repository.findById(id).ifPresent(tutor -> {
            tutor.setNome(nome);
            tutor.setTelefone(telefone);
            tutor.setEndereco(endereco);
            repository.save(tutor);
        });
    }

    public void excluir(String id) {
        repository.deleteById(id);
    }
}
