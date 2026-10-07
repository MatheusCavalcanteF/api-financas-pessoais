package com.finance.api.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	// Id e não @ManyToOne: evita N+1 na listagem paginada, e a posse da
	// categoria é validada no service antes de gravar
	@Column(name = "category_id")
	private UUID categoryId;

	// Sempre positivo; o sentido vem de type
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TransactionType type;

	@Column(name = "unique_hash", nullable = false, length = 64)
	private String uniqueHash;

	// Só é gravado pela importação OFX (INSERT nativo); quando presente, o hash veio dele
	@Column(name = "fitid", updatable = false, insertable = false)
	private String fitId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Transaction() {
	}

	public Transaction(UUID userId, UUID categoryId, BigDecimal amount, LocalDate date, String description,
			TransactionType type, String uniqueHash) {
		this.userId = userId;
		update(categoryId, amount, date, description, type, uniqueHash);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	// O hash por conteúdo muda junto com data, valor, tipo e descrição; o de FITID
	// é preservado pelo service
	public void update(UUID categoryId, BigDecimal amount, LocalDate date, String description, TransactionType type,
			String uniqueHash) {
		this.categoryId = categoryId;
		this.amount = amount;
		this.date = date;
		this.description = description;
		this.type = type;
		this.uniqueHash = uniqueHash;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getDate() {
		return date;
	}

	public String getDescription() {
		return description;
	}

	public TransactionType getType() {
		return type;
	}

	public String getUniqueHash() {
		return uniqueHash;
	}

	public String getFitId() {
		return fitId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
