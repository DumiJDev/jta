package dev.jta.demo.tutores;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA comum - nao sabe nada sobre JTA (mesmo ponto
 * que os demais repositorios do demo: a camada de persistencia de um app
 * JTA e identica a de qualquer app Spring).
 */
public interface TutorRepository extends JpaRepository<Tutor, String> {
}
