package com.videoplatform.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConnectionQualityMetricRepository extends JpaRepository<ConnectionQualityMetric, UUID> {

    Page<ConnectionQualityMetric> findBySessionIdOrderByRecordedAtAsc(UUID sessionId, Pageable pageable);

    List<ConnectionQualityMetric> findBySessionIdOrderByRecordedAtAsc(UUID sessionId);

    List<ConnectionQualityMetric> findByRoomId(String roomId);

    List<ConnectionQualityMetric> findByRoomIdOrderByRecordedAtAsc(String roomId);
}
