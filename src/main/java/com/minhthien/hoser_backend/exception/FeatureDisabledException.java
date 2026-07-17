package com.minhthien.hoser_backend.exception;

public class FeatureDisabledException extends RuntimeException {
    public FeatureDisabledException(String message) {
        super(message);
    }
}
