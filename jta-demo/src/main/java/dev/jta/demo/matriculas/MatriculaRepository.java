package dev.jta.demo.matriculas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatriculaRepository extends JpaRepository<Matricula, String> {

    List<Matricula> findByAlunoId(String alunoId);

    long countByTurmaId(String turmaId);
}
