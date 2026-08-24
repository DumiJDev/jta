package dev.jta.demo.produtos;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servico Spring comum - nao sabe nada sobre JTA. Prova o ponto central
 * da pergunta "integra com o backend do Spring": um componente JTA e so
 * mais um bean que pode receber qualquer servico via construtor, do
 * mesmo jeito que um {@code @RestController} receberia.
 *
 * <p>Persistencia real via {@link ProdutoRepository} (JPA + H2) - o
 * catalogo estatico em memoria da versao anterior virou dados de verdade,
 * seed via {@code data.sql} no boot da aplicacao.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    public List<Produto> listar() {
        return repository.findAll();
    }

    public Optional<Produto> buscar(String id) {
        return repository.findById(id);
    }

    public Produto criar(String nome, double preco) {
        Produto produto = new Produto(UUID.randomUUID().toString(), nome, preco);
        return repository.save(produto);
    }

    public void atualizar(String id, String nome, double preco) {
        repository.findById(id).ifPresent(produto -> {
            produto.setNome(nome);
            produto.setPreco(preco);
            repository.save(produto);
        });
    }

    public void excluir(String id) {
        repository.deleteById(id);
    }
}
