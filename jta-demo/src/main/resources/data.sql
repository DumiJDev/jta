INSERT INTO professor (id, nome, especialidade) VALUES ('1', 'Ana Ribeiro', 'Matematica');
INSERT INTO professor (id, nome, especialidade) VALUES ('2', 'Bruno Santos', 'Historia');

INSERT INTO disciplina (id, nome, professor_id) VALUES ('1', 'Matematica', '1');
INSERT INTO disciplina (id, nome, professor_id) VALUES ('2', 'Historia', '2');
INSERT INTO disciplina (id, nome, professor_id) VALUES ('3', 'Geometria', '1');

INSERT INTO turma (id, nome, ano) VALUES ('1', '9-A', '2026');
INSERT INTO turma (id, nome, ano) VALUES ('2', '9-B', '2026');

INSERT INTO aluno (id, nome, email, nascimento) VALUES ('1', 'Maria Silva', 'maria.silva@escola.exemplo', '2011-03-14');
INSERT INTO aluno (id, nome, email, nascimento) VALUES ('2', 'Joao Pereira', 'joao.pereira@escola.exemplo', '2011-07-22');
INSERT INTO aluno (id, nome, email, nascimento) VALUES ('3', 'Sofia Costa', 'sofia.costa@escola.exemplo', '2011-01-05');
INSERT INTO aluno (id, nome, email, nascimento) VALUES ('4', 'Miguel Fernandes', 'miguel.fernandes@escola.exemplo', '2011-11-30');

INSERT INTO matricula (id, aluno_id, turma_id) VALUES ('1', '1', '1');
INSERT INTO matricula (id, aluno_id, turma_id) VALUES ('2', '2', '1');
INSERT INTO matricula (id, aluno_id, turma_id) VALUES ('3', '3', '2');

INSERT INTO nota (id, aluno_id, disciplina_id, valor) VALUES ('1', '1', '1', 8.5);
INSERT INTO nota (id, aluno_id, disciplina_id, valor) VALUES ('2', '1', '2', 7.0);
INSERT INTO nota (id, aluno_id, disciplina_id, valor) VALUES ('3', '2', '1', 6.0);
