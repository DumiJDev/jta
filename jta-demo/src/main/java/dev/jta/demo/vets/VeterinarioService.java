package dev.jta.demo.vets;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VeterinarioService {

    private final VeterinarioRepository repository;

    public VeterinarioService(VeterinarioRepository repository) {
        this.repository = repository;
    }

    public List<Veterinario> listar() {
        return repository.findAll();
    }

    public Optional<Veterinario> buscar(String id) {
        return repository.findById(id);
    }

    public Veterinario criar(String nome, String especialidade) {
        Veterinario vet = new Veterinario(UUID.randomUUID().toString(), nome, especialidade);
        return repository.save(vet);
    }

    public void atualizar(String id, String nome, String especialidade) {
        repository.findById(id).ifPresent(vet -> {
            vet.setNome(nome);
            vet.setEspecialidade(especialidade);
            repository.save(vet);
        });
    }
}
