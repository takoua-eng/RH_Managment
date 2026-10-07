package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashboardRepository extends JpaRepository<Candidate, Long> {

    /**
     * Les 10 compétences les plus souvent manquantes d'après l'analyse IA.
     * Le CROSS JOIN LATERAL doit être placé AVANT le WHERE.
     */
    @Query(value = """
            SELECT s.skill AS skill, COUNT(*) AS cnt
            FROM candidates c
            JOIN job_offers j ON c.job_offer_id = j.id
            CROSS JOIN LATERAL jsonb_array_elements_text(
                CASE WHEN jsonb_typeof(c.ai_analysis -> 'competences_manquantes') = 'array'
                     THEN c.ai_analysis -> 'competences_manquantes'
                     ELSE CAST('[]' AS jsonb)
                END
            ) AS s(skill)
            WHERE c.ai_status = 'TERMINEE'
              AND c.application_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            GROUP BY s.skill
            ORDER BY cnt DESC
            LIMIT 10
            """, nativeQuery = true)
    List<Object[]> findTopMissingSkills(@Param("startDate") LocalDate startDate,
                                        @Param("dept") String dept);

    /** Délai moyen, en jours, entre la candidature et la décision finale. */
    @Query(value = """
            SELECT CAST(AVG(c.decision_date - c.application_date) AS double precision)
            FROM candidates c
            JOIN job_offers j ON c.job_offer_id = j.id
            WHERE c.decision_date IS NOT NULL
              AND c.application_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            """, nativeQuery = true)
    Double findAverageTimeToDecisionDays(@Param("startDate") LocalDate startDate,
                                         @Param("dept") String dept);

    /** Nombre de candidatures reçues par mois (format 'YYYY-MM'). */
    @Query(value = """
            SELECT TO_CHAR(c.application_date, 'YYYY-MM') AS m, COUNT(c.id) AS cnt
            FROM candidates c
            JOIN job_offers j ON c.job_offer_id = j.id
            WHERE c.application_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            GROUP BY TO_CHAR(c.application_date, 'YYYY-MM')
            ORDER BY m
            """, nativeQuery = true)
    List<Object[]> findMonthlyApplicationsCount(@Param("startDate") LocalDate startDate,
                                                @Param("dept") String dept);

    /** Nombre de candidats acceptés par mois de décision (format 'YYYY-MM'). */
    @Query(value = """
            SELECT TO_CHAR(c.decision_date, 'YYYY-MM') AS m, COUNT(c.id) AS cnt
            FROM candidates c
            JOIN job_offers j ON c.job_offer_id = j.id
            WHERE c.status = 'ACCEPTEE'
              AND c.decision_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            GROUP BY TO_CHAR(c.decision_date, 'YYYY-MM')
            ORDER BY m
            """, nativeQuery = true)
    List<Object[]> findMonthlyHiredCount(@Param("startDate") LocalDate startDate,
                                         @Param("dept") String dept);

    /**
     * Répartition des scores IA en 10 tranches (0 = 0-10 %, ..., 9 = 90-100 %).
     * LEAST(..., 9) place un score de 1.0 (100 %) dans la dernière tranche au lieu d'une 11e.
     */
    @Query(value = """
            SELECT CAST(LEAST(FLOOR(c.ai_score * 10), 9) AS integer) AS bucket, COUNT(*) AS cnt
            FROM candidates c
            JOIN job_offers j ON c.job_offer_id = j.id
            WHERE c.ai_score IS NOT NULL
              AND c.ai_status = 'TERMINEE'
              AND c.application_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            GROUP BY bucket
            ORDER BY bucket
            """, nativeQuery = true)
    List<Object[]> findScoreDistributionBuckets(@Param("startDate") LocalDate startDate,
                                                @Param("dept") String dept);

    /** Les 5 offres ayant reçu le plus de candidatures, avec leur score IA moyen. */
    @Query(value = """
            SELECT j.id, j.title, j.department, COUNT(c.id) AS app_count,
                   CAST(AVG(c.ai_score) AS double precision) AS avg_score
            FROM job_offers j
            JOIN candidates c ON c.job_offer_id = j.id
            WHERE c.application_date >= :startDate
              AND (CAST(:dept AS text) IS NULL OR j.department = :dept)
            GROUP BY j.id, j.title, j.department
            ORDER BY app_count DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> findTopOffersAggregated(@Param("startDate") LocalDate startDate,
                                           @Param("dept") String dept);
}