package com.pokerfriends.server.service;

import com.pokerfriends.server.model.TableState;
import com.pokerfriends.server.persistence.TableStateMetaEntity;
import com.pokerfriends.server.persistence.TableStateMetaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TableStateMetaService {
  private final TableStateMetaRepository tableStateMetaRepository;
  private final JdbcTemplate jdbcTemplate;
  private final AtomicBoolean stageConstraintFixed = new AtomicBoolean(false);

  public TableStateMetaService(TableStateMetaRepository tableStateMetaRepository, JdbcTemplate jdbcTemplate) {
    this.tableStateMetaRepository = tableStateMetaRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  public void upsert(TableState snapshot) {
    TableStateMetaEntity entity = buildEntity(snapshot);
    try {
      tableStateMetaRepository.save(entity);
      return;
    } catch (DataIntegrityViolationException ex) {
      if (!isStageConstraintViolation(ex)) {
        throw ex;
      }
      ensureStageConstraintCompatible();
    }
    tableStateMetaRepository.save(buildEntity(snapshot));
  }

  private TableStateMetaEntity buildEntity(TableState snapshot) {
    TableStateMetaEntity entity = tableStateMetaRepository.findByTableId(snapshot.getTableId())
        .orElseGet(() -> new TableStateMetaEntity(
            snapshot.getTableId(),
            snapshot.getHandId(),
            snapshot.getStage(),
            snapshot.getPot(),
            snapshot.getCurrentBet(),
            snapshot.getActionPlayerId()
        ));
    entity.setHandId(snapshot.getHandId());
    entity.setStage(snapshot.getStage());
    entity.setPot(snapshot.getPot());
    entity.setCurrentBet(snapshot.getCurrentBet());
    entity.setActionPlayerId(snapshot.getActionPlayerId());
    return entity;
  }

  private boolean isStageConstraintViolation(DataIntegrityViolationException exception) {
    Throwable root = exception;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    String message = root.getMessage();
    return message != null && message.contains("table_state_meta_stage_check");
  }

  private void ensureStageConstraintCompatible() {
    if (stageConstraintFixed.get()) {
      return;
    }
    synchronized (stageConstraintFixed) {
      if (stageConstraintFixed.get()) {
        return;
      }
      jdbcTemplate.execute("""
          ALTER TABLE table_state_meta
            DROP CONSTRAINT IF EXISTS table_state_meta_stage_check;
          ALTER TABLE table_state_meta
            ADD CONSTRAINT table_state_meta_stage_check
            CHECK (stage IN ('WAITING', 'PREFLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN', 'FINISHED'));
          """);
      stageConstraintFixed.set(true);
    }
  }
}
