package dev.jta.demo.notas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotaRepository extends JpaRepository<Nota, String> {

    List<Nota> findByAlunoId(String alunoId);
}
