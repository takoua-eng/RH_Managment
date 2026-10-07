package com.esprit.microservice.hrbackend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerNotificationDTO {

    private Long id;
    private String text;
    private String time;
    private String icon;
    private boolean unread;
    private String type;
    private String link;
}

