package com.pcom.common.core;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {
    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
