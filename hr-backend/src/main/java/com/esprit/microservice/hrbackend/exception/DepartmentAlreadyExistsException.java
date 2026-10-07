package com.esprit.microservice.hrbackend.exception;



public class DepartmentAlreadyExistsException extends RuntimeException {
    public DepartmentAlreadyExistsException(String name) {
        super("Un département avec le nom '" + name + "' existe déjà");
    }
}
