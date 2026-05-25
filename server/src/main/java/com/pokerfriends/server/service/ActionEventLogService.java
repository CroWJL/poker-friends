package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.dto.ActionEventResponse;
import com.pokerfriends.server.model.TableState;
import com.pokerfriends.server.persistence.ActionEventEntity;
import com.pokerfriends.server.persistence.ActionEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActionEventLogService {
  private final ActionEventRepository actionEventRepository;

  public ActionEventLogService(ActionEventRepository actionEventRepository) {
    this.actionEventRepository = actionEventRepository;
  }

  public void recordAccepted(String tableId, String playerId, ActionCommand command, TableState afterAction) {
    actionEventRepository.save(new ActionEventEntity(
        tableId,
        afterAction.getHandId(),
        playerId,
        command.type(),
        command.amount(),
        true,
        null,
        afterAction.getStage().name()
    ));
  }

  public void recordRejected(
      String tableId,
      String playerId,
      ActionCommand command,
      String stage,
      String errorMessage
  ) {
    actionEventRepository.save(new ActionEventEntity(
        tableId,
        "unknown",
        playerId,
        command == null ? null : command.type(),
        command == null ? null : command.amount(),
        false,
        errorMessage,
        stage
    ));
  }

  public List<ActionEventResponse> listRecentByTableId(String tableId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    return actionEventRepository.findByTableIdOrderByIdDesc(tableId, PageRequest.of(0, safeLimit))
        .stream()
        .map(event -> new ActionEventResponse(
            tableId,
            event.getHandId(),
            event.getPlayerId(),
            event.getActionType() == null ? null : event.getActionType().name(),
            event.getAmount(),
            event.isAccepted(),
            event.getErrorMessage(),
            event.getStage(),
            event.getCreatedAt()
        ))
        .toList();
  }
}
