package dev.jta.demo.produtos;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA real (nao mais um record em memoria) - persistida em H2
 * via {@link ProdutoRepository}. {@code @Table} explicito para nao
 * depender implicitamente da estrategia de nomenclatura padrao do
 * Hibernate ao decidir o nome da tabela.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    private String id;

    private String nome;

    private double preco;

    protected Produto() {
        // construtor sem argumentos exigido pelo JPA
    }

    public Produto(String id, String nome, double preco) {
        this.id = id;
        this.nome = nome;
        this.preco = preco;
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public double getPreco() {
        return preco;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public void setPreco(double preco) {
        this.preco = preco;
    }
}
