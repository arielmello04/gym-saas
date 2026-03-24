package com.gymsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.tenant.id = :tenantId
          AND s.canceled = false
          AND s.startAt >= :from
          AND s.endAt   <= :to
        ORDER BY s.startAt ASC
    """)
    List<ClassSession> findActiveSessionsBetween(
            @Param("tenantId") Long tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        SELECT s FROM ClassSession s
          JOIN FETCH s.classType t
        WHERE s.tenant.id = :tenantId
          AND s.startAt BETWEEN :from AND :to
          AND (:typeCode IS NULL OR t.code = :typeCode)
        ORDER BY s.startAt ASC
    """)
    List<ClassSession> findCalendar(
            @Param("tenantId") Long tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("typeCode") String typeCode);
}
