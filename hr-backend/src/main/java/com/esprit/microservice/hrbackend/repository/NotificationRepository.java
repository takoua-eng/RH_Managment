package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /** 
     * Récupère les notifications d'un destinataire (manager ou employé) 
     * triées par date décroissante.
     */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    
    /**
     * Compte le nombre de notifications non lues.
     */
    long countByRecipientIdAndUnreadTrue(Long recipientId);
    List<Notification> findTop5ByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}
