-- =============================================================================
-- RumoCap - usuário e banco de dados do projeto
--
-- Executado automaticamente por:  database\servidor.ps1 criar
--
-- Também pode ser usado em qualquer outro servidor PostgreSQL, conectado
-- como superusuário e informando a senha do usuário "rumocap":
--   psql -h localhost -p 5434 -U postgres -d postgres -v senha_rumocap=<senha> -f database/criar-banco.sql
-- Use a mesma senha em RUMOCAP_DB_PASSWORD (ou em config/application.properties).
--
-- Cria somente o usuário e o banco vazio. As tabelas e as categorias iniciais
-- são criadas pelo Flyway quando a aplicação inicia.
-- Pode ser executado novamente: o que já existir não é recriado, e a senha do
-- usuário é atualizada para a informada.
-- =============================================================================

SELECT 'CREATE ROLE rumocap WITH LOGIN'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rumocap')
\gexec

ALTER ROLE rumocap WITH LOGIN PASSWORD :'senha_rumocap';

SELECT 'CREATE DATABASE rumocap WITH OWNER = rumocap ENCODING = ''UTF8'' TEMPLATE = template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'rumocap')
\gexec

-- Somente o dono (rumocap) e superusuários podem conectar neste banco.
REVOKE CONNECT ON DATABASE rumocap FROM PUBLIC;
