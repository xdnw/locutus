package com.jpson;

public class PsonException extends Exception {
    public PsonException(String message) {
        super(message);
    }

    public PsonException(String message, Throwable cause) {
        super(message, cause);
    }

}
