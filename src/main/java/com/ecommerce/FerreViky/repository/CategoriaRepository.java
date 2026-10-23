package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Categoria;
import com.ecommerce.FerreViky.models.CodigoVirtual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

}