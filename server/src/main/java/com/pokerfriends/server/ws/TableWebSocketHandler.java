package com.pokerfriends.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.dto.WsEnvelope;
import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.model.PotAward;
import com.pokerfriends.server.model.SidePot;
import com.pokerfriends.server.model.TableStage;
import com.pokerfriends.server.model.TableState;
import com.pokerfriends.server.service.AuthTokenService;
import com.pokerfriends.server.service.ActionEventLogService;
import com.pokerfriends.server.service.RoomService;
import com.pokerfriends.server.service.TableEngineService;
import com.pokerfriends.server.service.TableStateMetaService;
import com.pokerfriends.server.service.TableStateSnapshotService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TableWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;
  private final AuthTokenService authTokenService;
  private final RoomService roomService;
  private final TableEngineService tableEngineService;
  private final TableStateMetaService tableStateMetaService;
  private final TableStateSnapshotService tableStateSnapshotService;
  private final ActionEventLogService actionEventLogService;
  private final Map<String, Map<String, WebSocketSession>> tableSessions = new ConcurrentHashMap<>();

  public TableWebSocketHandler(
      ObjectMapper objectMapper,
      AuthTokenService authTokenService,
      RoomService roomService,
      TableEngineService tableEngineService,
      TableStateMetaService tableStateMetaService,
      TableStateSnapshotService tableStateSnapshotService,
      ActionEventLogService actionEventLogService
  ) {
    this.objectMapper = objectMapper;
    this.authTokenService = authTokenService;
    this.roomService = roomService;
    this.tableEngineService = tableEngineService;
    this.tableStateMetaService = tableStateMetaService;
    this.tableStateSnapshotService = tableStateSnapshotService;
    this.actionEventLogService = actionEventLogService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String tableId = pathPart(session, 3);
    String playerId = queryParam(session.getUri(), "playerId");
    String token = queryParam(session.getUri(), "token");

    if (!authTokenService.validate(token, tableId, playerId)) {
      session.sendMessage(json(new WsEnvelope("ERROR", Map.of("message", "鉴权失败"))));
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }

    session.getAttributes().put("tableId", tableId);
    session.getAttributes().put("playerId", playerId);
    tableSessions.computeIfAbsent(tableId, k -> new ConcurrentHashMap<>()).put(playerId, session);
    try {
      roomService.ensureTableLoadedByTableId(tableId);
      var snapshotState = tableEngineService.getSnapshot(tableId);
      tableStateMetaService.upsert(snapshotState);
      tableStateSnapshotService.upsert(snapshotState);
      sendSnapshotToSession(session, snapshotState);
      broadcastTableSnapshot(tableId, snapshotState, session);
    } catch (Exception ex) {
      session.sendMessage(json(new WsEnvelope("ERROR", Map.of("message", ex.getMessage()))));
      session.close(CloseStatus.SERVER_ERROR);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    WsEnvelope envelope = objectMapper.readValue(message.getPayload(), WsEnvelope.class);
    String tableId = (String) session.getAttributes().get("tableId");
    String playerId = (String) session.getAttributes().get("playerId");

    if ("PING".equals(envelope.event())) {
      session.sendMessage(json(new WsEnvelope("PONG", Map.of("ok", true))));
      return;
    }

    if (!"ACTION".equals(envelope.event())) {
      session.sendMessage(json(new WsEnvelope("ERROR", Map.of("message", "未知事件类型"))));
      return;
    }

    try {
      ActionCommand command = objectMapper.convertValue(envelope.payload(), ActionCommand.class);
      tableEngineService.submitAction(tableId, playerId, command).thenAccept(snapshot -> {
        tableStateMetaService.upsert(snapshot);
        tableStateSnapshotService.upsert(snapshot);
        actionEventLogService.recordAccepted(tableId, playerId, command, snapshot);
        broadcastTableSnapshot(tableId, snapshot, null);
      }).join();
      session.sendMessage(json(new WsEnvelope("ACTION_RESULT", Map.of("accepted", true))));
    } catch (Exception ex) {
      ActionCommand rejected = null;
      try {
        rejected = objectMapper.convertValue(envelope.payload(), ActionCommand.class);
      } catch (Exception ignored) {
      }
      actionEventLogService.recordRejected(
          tableId,
          playerId,
          rejected,
          safeCurrentStage(tableId),
          ex.getMessage()
      );
      session.sendMessage(json(new WsEnvelope("ERROR", Map.of("message", ex.getMessage()))));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String tableId = (String) session.getAttributes().get("tableId");
    String playerId = (String) session.getAttributes().get("playerId");
    if (tableId == null || playerId == null) {
      return;
    }
    Map<String, WebSocketSession> sessions = tableSessions.get(tableId);
    if (sessions != null) {
      sessions.remove(playerId);
    }
  }

  private void broadcastTableSnapshot(String tableId, TableState snapshot, WebSocketSession skipSession) {
    Map<String, WebSocketSession> sessions = tableSessions.get(tableId);
    if (sessions == null) {
      return;
    }
    sessions.values().forEach(session -> {
      try {
        if (session.isOpen() && session != skipSession) {
          sendSnapshotToSession(session, snapshot);
        }
      } catch (Exception ignored) {
      }
    });
  }

  private void sendSnapshotToSession(WebSocketSession session, TableState fullSnapshot) throws Exception {
    String viewerPlayerId = (String) session.getAttributes().get("playerId");
    TableState masked = maskSnapshotForViewer(fullSnapshot, viewerPlayerId);
    session.sendMessage(json(new WsEnvelope("TABLE_SNAPSHOT", masked)));
  }

  private TableState maskSnapshotForViewer(TableState fullSnapshot, String viewerPlayerId) {
    TableState masked = new TableState(fullSnapshot.getTableId());
    masked.setHandId(fullSnapshot.getHandId());
    masked.setStage(fullSnapshot.getStage());
    masked.setPot(fullSnapshot.getPot());
    masked.setCurrentBet(fullSnapshot.getCurrentBet());
    masked.setActionCursor(fullSnapshot.getActionCursor());
    masked.setDealerCursor(fullSnapshot.getDealerCursor());
    masked.setActionsInStage(fullSnapshot.getActionsInStage());
    masked.setPlayersToAct(fullSnapshot.getPlayersToAct());
    masked.getCommunityCards().clear();
    masked.getCommunityCards().addAll(fullSnapshot.getCommunityCards());
    masked.setSidePots(fullSnapshot.getSidePots().stream()
        .map(sidePot -> new SidePot(sidePot.amount(), List.copyOf(sidePot.eligiblePlayerIds())))
        .toList());
    masked.setPotAwards(fullSnapshot.getPotAwards().stream()
        .map(award -> new PotAward(award.playerId(), award.amount()))
        .toList());
    masked.setRemainingDeck(List.of());
    masked.getPlayers().clear();
    for (PlayerState player : fullSnapshot.getPlayers()) {
      PlayerState copied = new PlayerState(
          player.getPlayerId(),
          player.getPlayerName(),
          player.getSeat(),
          player.getStack()
      );
      copied.setInHand(player.isInHand());
      copied.setWaitingForNextHand(player.isWaitingForNextHand());
      copied.setBetThisRound(player.getBetThisRound());
      copied.setTotalCommitted(player.getTotalCommitted());
      boolean isSelf = player.getPlayerId().equals(viewerPlayerId);
      boolean shouldReveal = isSelf || fullSnapshot.getStage() == TableStage.FINISHED;
      if (player.getHoleCards().size() == 2 && !shouldReveal) {
        copied.setHoleCards(List.of("??", "??"));
      } else {
        copied.setHoleCards(new ArrayList<>(player.getHoleCards()));
      }
      masked.getPlayers().add(copied);
    }
    return masked;
  }

  private TextMessage json(Object payload) throws Exception {
    return new TextMessage(objectMapper.writeValueAsString(payload));
  }

  private String queryParam(URI uri, String key) {
    if (uri == null || uri.getQuery() == null) {
      return "";
    }
    for (String kv : uri.getQuery().split("&")) {
      String[] split = kv.split("=", 2);
      if (split.length == 2 && split[0].equals(key)) {
        return split[1];
      }
    }
    return "";
  }

  private String pathPart(WebSocketSession session, int index) {
    String[] parts = session.getUri().getPath().split("/");
    if (parts.length <= index) {
      throw new IllegalStateException("无法解析 tableId");
    }
    return parts[index];
  }

  private String safeCurrentStage(String tableId) {
    try {
      return tableEngineService.getSnapshot(tableId).getStage().name();
    } catch (Exception ignored) {
      return "UNKNOWN";
    }
  }
}
