-- V13: confidenceScore — AI agent confidence metadata (Issue #112, Epic #100)
--
-- Nullable Double (0.0–1.0). Null means the WorkItem was not created by an AI
-- agent or no confidence metadata was provided.

ALTER TABLE work_item ADD COLUMN confidence_score DOUBLE PRECISION;
