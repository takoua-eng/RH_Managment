package com.esprit.microservice.hrbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidLeaveStatusTransitionException extends RuntimeException {
    public InvalidLeaveStatusTransitionException(String message) {
        super(message);
    }
}
