package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.model.TableStage;
import com.pokerfriends.server.model.TableState;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class BotOrchestratorService {
  public static final String BOT_PLAYER_ID_PREFIX = "bot-";

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingByTable = new ConcurrentHashMap<>();
  private final TableEngineService tableEngineService;
  private final BotDecisionService botDecisionService;

  public BotOrchestratorService(TableEngineService tableEngineService, BotDecisionService botDecisionService) {
    this.tableEngineService = tableEngineService;
    this.botDecisionService = botDecisionService;
  }

  public static boolean isBotPlayerId(String playerId) {
    return playerId != null && playerId.startsWith(BOT_PLAYER_ID_PREFIX);
  }

  public void scheduleBotTurnIfNeeded(String tableId, TableState snapshot, Consumer<BotActionResult> onAction) {
    if (!shouldSchedule(snapshot)) {
      return;
    }
    pendingByTable.compute(tableId, (id, existing) -> {
      if (existing != null) {
        existing.cancel(false);
      }
      return scheduler.schedule(() -> executeBotTurn(tableId, onAction), 550, TimeUnit.MILLISECONDS);
    });
  }

  private boolean shouldSchedule(TableState snapshot) {
    if (snapshot.getPracticeOutcome() != null && !snapshot.getPracticeOutcome().isBlank()) {
      return false;
    }
    String actionPlayerId = snapshot.getActionPlayerId();
    if (!isBotPlayerId(actionPlayerId)) {
      return false;
    }
    TableStage stage = snapshot.getStage();
    return stage != TableStage.WAITING && stage != TableStage.FINISHED && stage != TableStage.SHOWDOWN;
  }

  private void executeBotTurn(String tableId, Consumer<BotActionResult> onAction) {
    pendingByTable.remove(tableId);
    try {
      TableState current = tableEngineService.getSnapshot(tableId);
      if (!shouldSchedule(current)) {
        return;
      }
      String botPlayerId = current.getActionPlayerId();
      ActionCommand command = botDecisionService.decide(current, botPlayerId);
      TableState next = tableEngineService.submitAction(tableId, botPlayerId, command).join();
      onAction.accept(new BotActionResult(tableId, botPlayerId, command, next));
      scheduleBotTurnIfNeeded(tableId, next, onAction);
    } catch (Exception ignored) {
    }
  }

  public record BotActionResult(String tableId, String botPlayerId, ActionCommand command, TableState snapshot) {
  }
}
