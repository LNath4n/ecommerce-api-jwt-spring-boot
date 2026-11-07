package com.ecommerce.FerreViky.repository;

import com.ecommerce.FerreViky.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Checks whether a category name is already in use, ignoring case.
    boolean existsByNameIgnoreCase(String name);

    // Checks whether another category uses the name.
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // Deletes a category by ID and returns the number of rows deleted.
    @Modifying
    @Query("DELETE FROM Category c WHERE c.id = :id")
    int deleteCategoryById(@Param("id") Long id);

}