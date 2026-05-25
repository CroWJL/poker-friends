package com.pokerfriends.server.service;

import com.pokerfriends.server.model.TableState;
import com.pokerfriends.server.persistence.TableStateMetaEntity;
import com.pokerfriends.server.persistence.TableStateMetaRepository;
import org.springframework.stereotype.Service;

@Service
public class TableStateMetaService {
  private final TableStateMetaRepository tableStateMetaRepository;

  public TableStateMetaService(TableStateMetaRepository tableStateMetaRepository) {
    this.tableStateMetaRepository = tableStateMetaRepository;
  }

  public void upsert(TableState snapshot) {
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
    tableStateMetaRepository.save(entity);
  }
}
