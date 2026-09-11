-- Sprint 8: liga a Room a' ocorrencia que a originou (§4). Rooms criadas pelo
-- fluxo de Profile (Sprint 5) continuam com a coluna NULL. Indice unico parcial:
-- no maximo uma Room por ocorrencia.

ALTER TABLE rooms ADD COLUMN appointment_occurrence_id UUID REFERENCES appointment_occurrences (id);

CREATE UNIQUE INDEX idx_rooms_occurrence
    ON rooms (appointment_occurrence_id)
    WHERE appointment_occurrence_id IS NOT NULL;
