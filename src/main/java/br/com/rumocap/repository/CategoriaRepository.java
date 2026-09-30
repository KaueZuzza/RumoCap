package br.com.rumocap.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rumocap.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
}
