package com.pcom.common.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcom.common.core.BusinessException;
import com.pcom.common.core.ConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key support. The key record is written in the SAME transaction as the business action, so a
 * failed action leaves no record (client may retry) and concurrent duplicates are stopped by the unique index.
 */
public class IdempotencyService {
    public record Result<T>(T body, boolean replayed) {}

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper mapper;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public <T> Result<T> execute(String scope, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-128 characters", HttpStatus.BAD_REQUEST);
        }
        String hash = sha256(write(request));
        var existing = repository.findByScopeAndIdempotencyKey(scope, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used with a different request payload", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return new Result<>(read(existing.get().getResponseBody(), responseType), true);
        }
        T result = action.get();
        try {
            repository.saveAndFlush(new IdempotencyRecord(scope, key, hash, write(result)));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("IDEMPOTENT_REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is being processed");
        }
        return new Result<>(result, false);
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
