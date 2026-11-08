package com.pcom.common.core;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {
    public NotFoundException(String what, Object id) {
        super("NOT_FOUND", what + " not found: " + id, HttpStatus.NOT_FOUND);
    }
}
