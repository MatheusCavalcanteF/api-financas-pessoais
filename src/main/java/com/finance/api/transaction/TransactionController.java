package com.finance.api.transaction;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.finance.api.security.AuthenticatedUser;
import com.finance.api.transaction.dto.ImportSummaryResponse;
import com.finance.api.transaction.dto.TransactionRequest;
import com.finance.api.transaction.dto.TransactionResponse;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

	private final TransactionService transactionService;
	private final TransactionImportService transactionImportService;

	public TransactionController(TransactionService transactionService,
			TransactionImportService transactionImportService) {
		this.transactionService = transactionService;
		this.transactionImportService = transactionImportService;
	}

	@GetMapping
	public Page<TransactionResponse> list(
			@PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return transactionService.list(user.id(), pageable);
	}

	@GetMapping("/{id}")
	public TransactionResponse get(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return transactionService.get(id, user.id());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse create(@Valid @RequestBody TransactionRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return transactionService.create(request, user.id());
	}

	/**
	 * Importa um extrato OFX ou CSV (cabeçalho {@code date,amount,description,type},
	 * separado por vírgula ou ponto-e-vírgula). Lançamentos já importados são
	 * ignorados; qualquer linha inválida rejeita o arquivo inteiro.
	 */
	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportSummaryResponse importStatement(@RequestParam("file") MultipartFile file,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
		try (InputStream statement = file.getInputStream()) {
			return transactionImportService.importFile(statement, user.id());
		}
	}

	@PutMapping("/{id}")
	public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return transactionService.update(id, request, user.id());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		transactionService.delete(id, user.id());
	}

}
