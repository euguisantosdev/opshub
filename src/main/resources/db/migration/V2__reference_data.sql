-- Usuário técnico que assina os eventos gerados pelo próprio sistema (detecção e automação).
-- Está inativo e com um hash que não corresponde a nenhuma senha: não consegue fazer login.
INSERT INTO users (username, display_name, password_hash, role, active, created_at)
VALUES ('sistema', 'Sistema (automação)', '$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv', 'SISTEMA', FALSE, now());

-- Runbooks de referência (ações simuladas).
INSERT INTO runbooks (name, action, description, max_attempts) VALUES
    ('Reiniciar serviço', 'RESTART_SERVICE', 'Reinicia o serviço afetado e confere a saúde em seguida.', 2),
    ('Limpar cache', 'CLEAR_CACHE', 'Limpa o cache da aplicação e confere a saúde em seguida.', 1);
