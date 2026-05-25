package com.pokerfriends.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.model.PotAward;
import com.pokerfriends.server.model.SidePot;
import com.pokerfriends.server.model.TableStage;
import com.pokerfriends.server.model.TableState;
import com.pokerfriends.server.persistence.TableStateSnapshotEntity;
import com.pokerfriends.server.persistence.TableStateSnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TableStateSnapshotService {
  private final TableStateSnapshotRepository tableStateSnapshotRepository;
  private final ObjectMapper objectMapper;

  public TableStateSnapshotService(
      TableStateSnapshotRepository tableStateSnapshotRepository,
      ObjectMapper objectMapper
  ) {
    this.tableStateSnapshotRepository = tableStateSnapshotRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void upsert(TableState snapshot) {
    String json = serialize(snapshot);
    tableStateSnapshotRepository.findByTableId(snapshot.getTableId()).ifPresentOrElse(
        existing -> {
          existing.setSnapshotJson(json);
          tableStateSnapshotRepository.save(existing);
        },
        () -> {
          try {
            tableStateSnapshotRepository.save(new TableStateSnapshotEntity(snapshot.getTableId(), json));
          } catch (DataIntegrityViolationException duplicate) {
            // 并发下可能出现“同时插入同一 tableId”，回退为更新即可。
            tableStateSnapshotRepository.findByTableId(snapshot.getTableId()).ifPresent(existing -> {
              existing.setSnapshotJson(json);
              tableStateSnapshotRepository.save(existing);
            });
          }
        }
    );
  }

  @Transactional(readOnly = true)
  public Optional<TableState> load(String tableId) {
    Optional<TableStateSnapshotEntity> entity = tableStateSnapshotRepository.findByTableId(tableId);
    if (entity.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(deserialize(entity.get().getSnapshotJson(), tableId));
    } catch (RuntimeException ex) {
      // 兼容历史脏快照：加载失败时回退为空，由上层初始化新牌桌。
      return Optional.empty();
    }
  }

  private String serialize(TableState state) {
    try {
      return objectMapper.writeValueAsString(state);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("牌桌快照序列化失败", ex);
    }
  }

  private TableState deserialize(String json, String fallbackTableId) {
    try {
      JsonNode root = objectMapper.readTree(json);
      String tableId = root.path("tableId").asText("");
      if (tableId.isBlank()) {
        tableId = fallbackTableId;
      }
      if (tableId == null || tableId.isBlank()) {
        throw new IllegalStateException("快照缺少 tableId");
      }
      TableState state = new TableState(tableId);
      state.setHandId(root.path("handId").asText(state.getHandId()));
      state.setStage(TableStage.valueOf(root.path("stage").asText(state.getStage().name())));
      state.setPot(root.path("pot").asInt(0));
      state.setCurrentBet(root.path("currentBet").asInt(0));
      state.setActionCursor(root.path("actionCursor").asInt(0));
      state.setDealerCursor(root.path("dealerCursor").asInt(-1));
      state.setActionsInStage(root.path("actionsInStage").asInt(0));
      state.setPlayersToAct(root.path("playersToAct").asInt(0));

      JsonNode communityCards = root.path("communityCards");
      if (communityCards.isArray()) {
        state.getCommunityCards().clear();
        communityCards.forEach(card -> state.getCommunityCards().add(card.asText()));
      }

      JsonNode remainingDeck = root.path("remainingDeck");
      if (remainingDeck.isArray()) {
        state.setRemainingDeck(asStringList(remainingDeck));
      }

      JsonNode players = root.path("players");
      if (players.isArray()) {
        state.getPlayers().clear();
        players.forEach(player -> {
          PlayerState restored = new PlayerState(
              player.path("playerId").asText(),
              player.path("playerName").asText(),
              player.path("seat").asInt(),
              player.path("stack").asInt()
          );
          restored.setInHand(player.path("inHand").asBoolean(true));
          restored.setWaitingForNextHand(player.path("waitingForNextHand").asBoolean(false));
          restored.setBetThisRound(player.path("betThisRound").asInt(0));
          restored.setTotalCommitted(player.path("totalCommitted").asInt(0));
          JsonNode holeCards = player.path("holeCards");
          if (holeCards.isArray()) {
            restored.setHoleCards(asStringList(holeCards));
          }
          state.getPlayers().add(restored);
        });
      }

      JsonNode sidePots = root.path("sidePots");
      if (sidePots.isArray()) {
        state.setSidePots(
            asArray(sidePots).stream()
                .map(node -> new SidePot(
                    node.path("amount").asInt(0),
                    asStringList(node.path("eligiblePlayerIds"))
                ))
                .toList()
        );
      }

      JsonNode potAwards = root.path("potAwards");
      if (potAwards.isArray()) {
        state.setPotAwards(
            asArray(potAwards).stream()
                .map(node -> new PotAward(node.path("playerId").asText(), node.path("amount").asInt(0)))
                .toList()
        );
      }
      return state;
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("牌桌快照反序列化失败", ex);
    }
  }

  private List<String> asStringList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    return asArray(node).stream().map(JsonNode::asText).toList();
  }

  private List<JsonNode> asArray(JsonNode node) {
    List<JsonNode> list = new java.util.ArrayList<>();
    node.forEach(list::add);
    return list;
  }
}
