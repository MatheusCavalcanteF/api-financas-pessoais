package com.finance.api.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	List<Category> findAllByUserIdOrderByNameAsc(UUID userId);

	Optional<Category> findByIdAndUserId(UUID id, UUID userId);

	boolean existsByIdAndUserId(UUID id, UUID userId);

	boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

}
