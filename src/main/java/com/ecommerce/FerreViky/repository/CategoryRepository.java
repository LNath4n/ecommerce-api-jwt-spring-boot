package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Modifying
    @Query("DELETE FROM Category c WHERE c.id = :id")
    int deleteCategoryById(@Param("id") Long id);

}