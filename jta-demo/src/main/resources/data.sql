INSERT INTO tutor (id, nome, telefone, endereco) VALUES ('1', 'Maria Silva', '(11) 91234-5678', 'Rua das Flores, 123');
INSERT INTO tutor (id, nome, telefone, endereco) VALUES ('2', 'Joao Pereira', '(11) 98888-1122', 'Av. Central, 456');

INSERT INTO pet (id, nome, especie, tutor_id) VALUES ('1', 'Rex', 'Cachorro', '1');
INSERT INTO pet (id, nome, especie, tutor_id) VALUES ('2', 'Mimi', 'Gato', '2');

INSERT INTO visita (id, data, descricao, pet_id) VALUES ('1', '2026-06-15', 'Vacina antirrabica', '1');
INSERT INTO visita (id, data, descricao, pet_id) VALUES ('2', '2026-07-02', 'Check-up de rotina', '2');

INSERT INTO veterinario (id, nome, especialidade) VALUES ('1', 'Dra. Ana Costa', 'Clinica geral');
INSERT INTO veterinario (id, nome, especialidade) VALUES ('2', 'Dr. Carlos Souza', 'Cirurgia');
