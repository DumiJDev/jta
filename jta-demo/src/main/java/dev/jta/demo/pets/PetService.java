package dev.jta.demo.pets;

import dev.jta.demo.tutores.Tutor;
import dev.jta.demo.tutores.TutorRepository;
import dev.jta.demo.visitas.Visita;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Servico Spring comum. {@link #criar} recebe o id do tutor (resolvido a
 * partir do path param aninhado {@code /tutores/{tutorId}/pets/novo}) e
 * carrega o {@link Tutor} de verdade antes de persistir o {@link Pet} -
 * a mesma ideia de {@code ProdutoService}, agora com uma relacao real.
 *
 * <p>{@link #registrarVisita} adiciona uma {@link Visita} a colecao do
 * pet e salva o agregado - a cascata declarada em
 * {@link Pet#getVisitas()} persiste a visita nova sem precisar de um
 * repositorio dedicado para {@link Visita}.
 */
@Service
public class PetService {

    private final PetRepository repository;
    private final TutorRepository tutorRepository;

    public PetService(PetRepository repository, TutorRepository tutorRepository) {
        this.repository = repository;
        this.tutorRepository = tutorRepository;
    }

    public Optional<Pet> buscar(String id) {
        return repository.findById(id);
    }

    public Pet criar(String tutorId, String nome, String especie) {
        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("Tutor '" + tutorId + "' nao encontrado"));
        Pet pet = new Pet(UUID.randomUUID().toString(), nome, especie, tutor);
        return repository.save(pet);
    }

    public void atualizar(String id, String nome, String especie) {
        repository.findById(id).ifPresent(pet -> {
            pet.setNome(nome);
            pet.setEspecie(especie);
            repository.save(pet);
        });
    }

    public void registrarVisita(String petId, String data, String descricao) {
        repository.findById(petId).ifPresent(pet -> {
            pet.getVisitas().add(new Visita(UUID.randomUUID().toString(), data, descricao, pet));
            repository.save(pet);
        });
    }

    public void excluir(String id) {
        repository.deleteById(id);
    }
}
