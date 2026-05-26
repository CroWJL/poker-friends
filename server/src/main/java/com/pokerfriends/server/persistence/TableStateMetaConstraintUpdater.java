package com.pokerfriends.server.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class TableStateMetaConstraintUpdater {
  private final JdbcTemplate jdbcTemplate;

  public TableStateMetaConstraintUpdater(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void ensureStageCheckConstraint() {
    jdbcTemplate.execute("""
        DO $$
        BEGIN
          IF EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = 'table_state_meta'
          ) THEN
            ALTER TABLE table_state_meta
              DROP CONSTRAINT IF EXISTS table_state_meta_stage_check;
            ALTER TABLE table_state_meta
              ADD CONSTRAINT table_state_meta_stage_check
              CHECK (stage IN ('WAITING', 'PREFLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN', 'FINISHED'));
          END IF;
        END
        $$;
        """);
  }
}
