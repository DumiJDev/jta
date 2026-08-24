package dev.jta.demo.produtos;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA comum - nao sabe nada sobre JTA. Prova o
 * mesmo ponto que {@link ProdutoService}: a camada de persistencia de um
 * app JTA e identica a de qualquer app Spring.
 */
public interface ProdutoRepository extends JpaRepository<Produto, String> {
}
