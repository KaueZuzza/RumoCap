-- =============================================================================
-- Categorias iniciais do guia.
-- Novas categorias podem ser criadas pela área administrativa (admin.html)
-- ou por uma nova migration (ex.: V3__nova_categoria.sql).
--
-- Nenhum estabelecimento é inserido aqui: os dados reais serão cadastrados
-- manualmente pela área administrativa.
-- =============================================================================

INSERT INTO categoria (nome) VALUES
    ('Alimentação'),
    ('Saúde'),
    ('Mercados'),
    ('Moda e Beleza'),
    ('Tecnologia'),
    ('Serviços'),
    ('Casa e Construção'),
    ('Automotivo'),
    ('Outros');
