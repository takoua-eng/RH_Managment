package com.esprit.microservice.hrbackend.event;

public record JobOfferPublishedEvent(Long jobOfferId, String title, String departmentName) {
}
