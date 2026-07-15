package com.minhthien.hoser_backend.exception;

import org.springframework.http.HttpStatus;

public class VnptEkycException extends RuntimeException {
    private final HttpStatus status;

    public VnptEkycException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
