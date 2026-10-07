package com.esprit.microservice.hrbackend.entity;

public enum LeaveStatus {
    PENDING,    // en attente de la décision du manager
    APPROVED,   // accepté par le manager (décision finale)
    REJECTED,   // refusé par le manager (décision finale)
    CANCELLED   // annulé par l'employé (si cette fonctionnalité existe)
}
