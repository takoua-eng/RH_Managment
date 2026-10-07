package com.esprit.microservice.hrbackend.exception;



public class DepartmentNotFoundException extends RuntimeException {
    public DepartmentNotFoundException(Long id) {
        super("Département introuvable avec l'id : " + id);
    }
}
