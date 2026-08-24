package dev.jta.demo.alunos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlunoRepository extends JpaRepository<Aluno, String> {

    List<Aluno> findByNomeContainingIgnoreCaseOrderByNome(String nome);
}
