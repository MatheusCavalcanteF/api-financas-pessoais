package com.finance.api.category.rule;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "category_rules")
public class CategoryRule {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	// Id e não @ManyToOne, como em Transaction: a posse é validada no service
	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	// Como o usuário digitou (sem espaços extras); a comparação usa a forma normalizada
	@Column(nullable = false, length = 100)
	private String keyword;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CategoryRule() {
	}

	public CategoryRule(UUID userId, String keyword, UUID categoryId) {
		this.userId = userId;
		update(keyword, categoryId);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void update(String keyword, UUID categoryId) {
		this.keyword = keyword;
		this.categoryId = categoryId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getCategoryId() {
		return categoryId;
	}

	public String getKeyword() {
		return keyword;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
