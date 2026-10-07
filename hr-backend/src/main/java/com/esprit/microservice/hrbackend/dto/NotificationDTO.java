package com.esprit.microservice.hrbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private Long id;
    private String text;
    private String time;
    private String icon;
    private boolean unread;
    private String type;
    private String link;
}
