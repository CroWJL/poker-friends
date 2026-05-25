package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.model.PlayerActionType;
import com.pokerfriends.server.model.PotAward;
import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.model.SidePot;
import com.pokerfriends.server.model.TableStage;
import com.pokerfriends.server.model.TableState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class TableEngineService {
  private final Map<String, TableActor> tables = new ConcurrentHashMap<>();
  private final DeckService deckService;
  private final HandEvaluatorService handEvaluatorService;

  TableEngineService() {
    this(new DeckService(), new HandEvaluatorService());
  }

  @Autowired
  public TableEngineService(DeckService deckService, HandEvaluatorService handEvaluatorService) {
    this.deckService = deckService;
    this.handEvaluatorService = handEvaluatorService;
  }

  public void initTable(String tableId, int smallBlind, int bigBlind, int maxPlayers) {
    tables.computeIfAbsent(tableId, id -> new TableActor(
        id, smallBlind, bigBlind, maxPlayers, deckService, handEvaluatorService
    ));
  }

  public void restoreTable(String tableId, int smallBlind, int bigBlind, int maxPlayers, TableState snapshot) {
    if (snapshot == null) {
      initTable(tableId, smallBlind, bigBlind, maxPlayers);
      return;
    }
    tables.compute(tableId, (id, existing) -> new TableActor(
        id,
        smallBlind,
        bigBlind,
        maxPlayers,
        deckService,
        handEvaluatorService,
        snapshot
    ));
  }

  public void addPlayer(String tableId, String playerId, String playerName) {
    TableActor actor = getActor(tableId);
    actor.run(() -> {
      actor.addPlayer(playerId, playerName);
      return null;
    }).join();
  }

  public int playerCount(String tableId) {
    return getActor(tableId).state().getPlayers().size();
  }

  public CompletableFuture<TableState> submitAction(String tableId, String playerId, ActionCommand command) {
    return getActor(tableId).run(() -> {
      getActor(tableId).applyAction(playerId, command);
      return getActor(tableId).state();
    });
  }

  public TableState getSnapshot(String tableId) {
    return getActor(tableId).copyState();
  }

  private TableActor getActor(String tableId) {
    TableActor actor = tables.get(tableId);
    if (actor == null) {
      throw new IllegalArgumentException("牌桌不存在");
    }
    return actor;
  }

  private static final class TableActor {
    private final ExecutorService executor;
    private final TableState state;
    private final int smallBlind;
    private final int bigBlind;
    private final int maxPlayers;
    private final DeckService deckService;
    private final HandEvaluatorService handEvaluatorService;
    private List<String> deck;
    private int dealerCursor;

    private TableActor(
        String tableId,
        int smallBlind,
        int bigBlind,
        int maxPlayers,
        DeckService deckService,
        HandEvaluatorService handEvaluatorService
    ) {
      this.executor = Executors.newSingleThreadExecutor();
      this.state = new TableState(tableId);
      this.smallBlind = smallBlind;
      this.bigBlind = bigBlind;
      this.maxPlayers = maxPlayers;
      this.deckService = deckService;
      this.handEvaluatorService = handEvaluatorService;
      this.deck = List.of();
      this.dealerCursor = -1;
    }

    private TableActor(
        String tableId,
        int smallBlind,
        int bigBlind,
        int maxPlayers,
        DeckService deckService,
        HandEvaluatorService handEvaluatorService,
        TableState snapshot
    ) {
      this.executor = Executors.newSingleThreadExecutor();
      this.state = snapshot;
      this.smallBlind = smallBlind;
      this.bigBlind = bigBlind;
      this.maxPlayers = maxPlayers;
      this.deckService = deckService;
      this.handEvaluatorService = handEvaluatorService;
      this.deck = new ArrayList<>(snapshot.getRemainingDeck());
      this.dealerCursor = snapshot.getDealerCursor();
      syncRemainingDeck();
    }

    private <T> CompletableFuture<T> run(Task<T> task) {
      return CompletableFuture.supplyAsync(() -> task.run(), executor);
    }

    private void addPlayer(String playerId, String playerName) {
      if (state.getPlayers().size() >= maxPlayers) {
        throw new IllegalStateException("牌桌人数已满");
      }
      int nextSeat = state.getPlayers().size() + 1;
      PlayerState joinedPlayer = new PlayerState(playerId, playerName, nextSeat, 1000);
      boolean waiting = isCurrentHandInProgress();
      joinedPlayer.setWaitingForNextHand(waiting);
      joinedPlayer.setInHand(!waiting);
      state.getPlayers().add(joinedPlayer);
      state.getPlayers().sort(Comparator.comparingInt(PlayerState::getSeat));
      if (state.getPlayers().size() == 2 && state.getPot() == 0) {
        postBlind();
      } else if (!joinedPlayer.isWaitingForNextHand() && isBeforePreflopFirstAction()) {
        dealHoleCards(joinedPlayer);
      }
    }

    private void postBlind() {
      startHandWithRotatingBlinds();
    }

    private void startHandWithRotatingBlinds() {
      if (countPlayersWithChips() < 2) {
        state.setStage(TableStage.FINISHED);
        state.setPlayersToAct(0);
        return;
      }
      startNewHand();
      List<Integer> eligibleSeats = eligibleSeatIndexes();
      rotateDealer(eligibleSeats);
      int sbIndex = eligibleSeats.size() == 2 ? dealerCursor : nextEligibleIndex(dealerCursor, eligibleSeats);
      int bbIndex = nextEligibleIndex(sbIndex, eligibleSeats);
      PlayerState sb = state.getPlayers().get(sbIndex);
      PlayerState bb = state.getPlayers().get(bbIndex);
      applyBet(sb, Math.min(smallBlind, sb.getStack()));
      applyBet(bb, Math.min(bigBlind, bb.getStack()));
      refreshSidePots();
      state.setCurrentBet(bb.getBetThisRound());
      int firstActionIndex = eligibleSeats.size() == 2 ? dealerCursor : nextEligibleIndex(bbIndex, eligibleSeats);
      state.setActionCursor(firstActionIndex);
      state.setPlayersToAct(countPlayersCanActInCurrentRound());
    }

    private void applyAction(String playerId, ActionCommand command) {
      if (command == null || command.type() == null) {
        throw new IllegalArgumentException("动作不能为空");
      }
      PlayerState actionPlayer = currentActionPlayer();
      if (!actionPlayer.getPlayerId().equals(playerId)) {
        throw new IllegalStateException("还没轮到你行动");
      }
      int currentBetBeforeAction = state.getCurrentBet();
      switch (command.type()) {
        case FOLD -> actionPlayer.setInHand(false);
        case CHECK -> {
          if (actionPlayer.getBetThisRound() != state.getCurrentBet()) {
            throw new IllegalStateException("当前不能 check");
          }
        }
        case CALL -> {
          int callAmount = state.getCurrentBet() - actionPlayer.getBetThisRound();
          applyBet(actionPlayer, callAmount);
        }
        case RAISE -> {
          int amount = command.amount() == null ? 0 : command.amount();
          if (amount <= state.getCurrentBet()) {
            throw new IllegalStateException("raise 金额必须大于当前注");
          }
          int target = amount - actionPlayer.getBetThisRound();
          applyBet(actionPlayer, target);
          state.setCurrentBet(amount);
        }
        case ALL_IN -> {
          int amount = actionPlayer.getStack();
          applyBet(actionPlayer, amount);
          if (actionPlayer.getBetThisRound() > state.getCurrentBet()) {
            state.setCurrentBet(actionPlayer.getBetThisRound());
          }
        }
      }
      refreshSidePots();
      boolean raised = state.getCurrentBet() > currentBetBeforeAction;
      progressTurn(raised);
    }

    private void progressTurn(boolean raised) {
      List<PlayerState> alive = state.getPlayers().stream().filter(PlayerState::isInHand).toList();
      if (alive.size() <= 1) {
        settleByLastPlayerStanding();
        return;
      }
      int playersCanAct = countPlayersCanActInCurrentRound();
      if (playersCanAct <= 1) {
        runOutBoardAndSettle();
        return;
      }
      if (raised) {
        state.setPlayersToAct(playersCanAct - 1);
      } else {
        state.setPlayersToAct(Math.max(0, state.getPlayersToAct() - 1));
      }
      state.setActionsInStage(state.getActionsInStage() + 1);
      if (state.getPlayersToAct() == 0) {
        advanceStage();
      }
      if (state.getStage() == TableStage.SHOWDOWN) {
        settleAtShowdown();
        return;
      }
      moveToNextActionPlayer();
    }

    private void advanceStage() {
      state.setActionsInStage(0);
      state.getPlayers().forEach(player -> player.setBetThisRound(0));
      state.setCurrentBet(0);
      switch (state.getStage()) {
        case PREFLOP -> {
          state.setStage(TableStage.FLOP);
          dealCommunityCards(3);
          if (dealerCursor >= 0) {
            state.setActionCursor(dealerCursor);
          }
        }
        case FLOP -> {
          state.setStage(TableStage.TURN);
          dealCommunityCards(1);
          if (dealerCursor >= 0) {
            state.setActionCursor(dealerCursor);
          }
        }
        case TURN -> {
          state.setStage(TableStage.RIVER);
          dealCommunityCards(1);
          if (dealerCursor >= 0) {
            state.setActionCursor(dealerCursor);
          }
        }
        case RIVER -> state.setStage(TableStage.SHOWDOWN);
        case SHOWDOWN -> state.setStage(TableStage.FINISHED);
        case FINISHED -> {
        }
      }
      if (state.getStage() == TableStage.FINISHED) {
        state.setPlayersToAct(0);
      } else {
        state.setPlayersToAct(countPlayersCanActInCurrentRound());
      }
    }

    private int countPlayersCanActInCurrentRound() {
      return (int) state.getPlayers().stream()
          .filter(player -> player.isInHand() && player.getStack() > 0)
          .count();
    }

    private void moveToNextActionPlayer() {
      int nextIndex = state.getActionCursor();
      for (int i = 0; i < state.getPlayers().size(); i++) {
        nextIndex = (nextIndex + 1) % state.getPlayers().size();
        PlayerState nextPlayer = state.getPlayers().get(nextIndex);
        if (nextPlayer.isInHand() && nextPlayer.getStack() > 0) {
          state.setActionCursor(nextIndex);
          return;
        }
      }
    }

    private PlayerState currentActionPlayer() {
      if (state.getPlayers().isEmpty()) {
        throw new IllegalStateException("牌桌没有玩家");
      }
      return state.getPlayers().get(state.getActionCursor());
    }

    private void applyBet(PlayerState player, int amount) {
      if (amount < 0) {
        throw new IllegalStateException("下注金额不能为负数");
      }
      if (amount > player.getStack()) {
        throw new IllegalStateException("筹码不足");
      }
      player.setStack(player.getStack() - amount);
      player.setBetThisRound(player.getBetThisRound() + amount);
      player.setTotalCommitted(player.getTotalCommitted() + amount);
      state.setPot(state.getPot() + amount);
    }

    private void refreshSidePots() {
      state.setSidePots(calculateSidePots());
    }

    private void settleByLastPlayerStanding() {
      PlayerState winner = state.getPlayers().stream()
          .filter(PlayerState::isInHand)
          .findFirst()
          .orElse(null);
      if (winner != null) {
        state.setPotAwards(List.of(new PotAward(winner.getPlayerId(), state.getPot())));
        winner.setStack(winner.getStack() + state.getPot());
      }
      clearPotsAndFinalizeHand();
    }

    private void runOutBoardAndSettle() {
      while (state.getStage() != TableStage.SHOWDOWN && state.getStage() != TableStage.FINISHED) {
        advanceStage();
      }
      if (state.getStage() == TableStage.SHOWDOWN) {
        settleAtShowdown();
      }
    }

    private void settleAtShowdown() {
      Map<String, Long> handStrength = evaluateHandStrengthForActivePlayers();
      Map<String, Integer> payoutByPlayer = new HashMap<>();
      for (SidePot sidePot : state.getSidePots()) {
        List<PlayerState> contenders = sidePot.eligiblePlayerIds().stream()
            .map(this::findPlayer)
            .filter(player -> player != null && player.isInHand())
            .toList();
        if (contenders.isEmpty()) {
          continue;
        }
        long bestScore = contenders.stream()
            .mapToLong(player -> handStrength.getOrDefault(player.getPlayerId(), Long.MIN_VALUE))
            .max()
            .orElse(Long.MIN_VALUE);
        List<PlayerState> winners = contenders.stream()
            .filter(player -> handStrength.getOrDefault(player.getPlayerId(), Long.MIN_VALUE) == bestScore)
            .sorted(Comparator.comparingInt(PlayerState::getSeat))
            .toList();
        int split = sidePot.amount() / winners.size();
        int remainder = sidePot.amount() % winners.size();
        for (int i = 0; i < winners.size(); i++) {
          PlayerState winner = winners.get(i);
          int award = split + (i < remainder ? 1 : 0);
          payoutByPlayer.merge(winner.getPlayerId(), award, Integer::sum);
        }
      }
      List<PotAward> awards = payoutByPlayer.entrySet().stream()
          .filter(entry -> entry.getValue() > 0)
          .map(entry -> new PotAward(entry.getKey(), entry.getValue()))
          .sorted(Comparator.comparingInt(award -> findPlayer(award.playerId()).getSeat()))
          .toList();
      state.setPotAwards(awards);
      for (PotAward award : awards) {
        PlayerState winner = findPlayer(award.playerId());
        if (winner != null) {
          winner.setStack(winner.getStack() + award.amount());
        }
      }
      clearPotsAndFinalizeHand();
    }

    private PlayerState findPlayer(String playerId) {
      return state.getPlayers().stream()
          .filter(player -> player.getPlayerId().equals(playerId))
          .findFirst()
          .orElse(null);
    }

    private void clearPotsAndFinalizeHand() {
      state.setPot(0);
      state.setCurrentBet(0);
      state.setPlayersToAct(0);
      state.getPlayers().forEach(player -> player.setBetThisRound(0));
      state.setSidePots(List.of());
      state.setStage(TableStage.FINISHED);
      if (countPlayersWithChips() >= 2) {
        startHandWithRotatingBlinds();
      }
    }

    private boolean isBeforePreflopFirstAction() {
      return state.getStage() == TableStage.PREFLOP
          && state.getActionsInStage() == 0
          && state.getCommunityCards().isEmpty()
          && !deck.isEmpty();
    }

    private void startNewHand() {
      state.setStage(TableStage.PREFLOP);
      state.setPot(0);
      state.setCurrentBet(0);
      state.setActionsInStage(0);
      state.setPlayersToAct(0);
      state.setActionCursor(0);
      state.setDealerCursor(dealerCursor);
      state.getCommunityCards().clear();
      state.setSidePots(List.of());
      state.setPotAwards(List.of());
      deck = new ArrayList<>(deckService.shuffledDeck());
      syncRemainingDeck();
      for (PlayerState player : state.getPlayers()) {
        player.setInHand(player.getStack() > 0);
        player.setWaitingForNextHand(false);
        player.setBetThisRound(0);
        player.setTotalCommitted(0);
        player.setHoleCards(List.of());
      }
      state.getPlayers().stream()
          .filter(player -> player.isInHand() && player.getStack() > 0)
          .forEach(this::dealHoleCards);
    }

    private void dealHoleCards(PlayerState player) {
      if (!player.isInHand() || player.getHoleCards().size() == 2) {
        return;
      }
      player.setHoleCards(List.of(drawCard(), drawCard()));
    }

    private void dealCommunityCards(int amount) {
      for (int i = 0; i < amount; i++) {
        state.getCommunityCards().add(drawCard());
      }
    }

    private String drawCard() {
      if (deck.isEmpty()) {
        throw new IllegalStateException("牌堆已空");
      }
      String card = deck.remove(deck.size() - 1);
      syncRemainingDeck();
      return card;
    }

    private Map<String, Long> evaluateHandStrengthForActivePlayers() {
      return state.getPlayers().stream()
          .filter(PlayerState::isInHand)
          .filter(player -> player.getHoleCards().size() == 2)
          .collect(Collectors.toMap(
              PlayerState::getPlayerId,
              player -> handEvaluatorService.evaluateSevenCards(mergeCards(player.getHoleCards(), state.getCommunityCards()))
          ));
    }

    private List<String> mergeCards(List<String> holeCards, List<String> communityCards) {
      List<String> cards = new ArrayList<>(holeCards);
      cards.addAll(communityCards);
      if (cards.size() != 7) {
        throw new IllegalStateException("无法比牌，牌面数量不是 7");
      }
      return cards;
    }

    private void syncRemainingDeck() {
      state.setRemainingDeck(deck);
    }

    private int countPlayersWithChips() {
      return (int) state.getPlayers().stream().filter(player -> player.getStack() > 0).count();
    }

    private List<Integer> eligibleSeatIndexes() {
      List<Integer> result = new ArrayList<>();
      for (int i = 0; i < state.getPlayers().size(); i++) {
        if (state.getPlayers().get(i).getStack() > 0) {
          result.add(i);
        }
      }
      return result;
    }

    private boolean isCurrentHandInProgress() {
      return state.getPlayers().size() >= 2
          && state.getStage() != TableStage.FINISHED
          && (state.getPot() > 0 || state.getActionsInStage() > 0 || !state.getCommunityCards().isEmpty());
    }

    private void rotateDealer(List<Integer> eligibleSeats) {
      if (eligibleSeats.isEmpty()) {
        dealerCursor = -1;
        state.setDealerCursor(-1);
        return;
      }
      if (dealerCursor < 0 || !eligibleSeats.contains(dealerCursor)) {
        dealerCursor = eligibleSeats.get(0);
      } else {
        dealerCursor = nextEligibleIndex(dealerCursor, eligibleSeats);
      }
      state.setDealerCursor(dealerCursor);
    }

    private int nextEligibleIndex(int fromIndex, List<Integer> eligibleSeats) {
      int cursor = fromIndex;
      for (int i = 0; i < state.getPlayers().size(); i++) {
        cursor = (cursor + 1) % state.getPlayers().size();
        if (eligibleSeats.contains(cursor)) {
          return cursor;
        }
      }
      throw new IllegalStateException("无法找到可行动座位");
    }

    private List<SidePot> calculateSidePots() {
      List<PlayerState> contributors = state.getPlayers().stream()
          .filter(player -> player.getTotalCommitted() > 0)
          .toList();
      if (contributors.isEmpty()) {
        return List.of();
      }
      List<Integer> levels = contributors.stream()
          .map(PlayerState::getTotalCommitted)
          .distinct()
          .sorted()
          .toList();
      List<SidePot> sidePots = new java.util.ArrayList<>();
      int previousLevel = 0;
      for (int level : levels) {
        int contributingCount = (int) contributors.stream()
            .filter(player -> player.getTotalCommitted() >= level)
            .count();
        int amount = (level - previousLevel) * contributingCount;
        if (amount <= 0) {
          continue;
        }
        List<String> eligiblePlayerIds = state.getPlayers().stream()
            .filter(PlayerState::isInHand)
            .filter(player -> player.getTotalCommitted() >= level)
            .map(PlayerState::getPlayerId)
            .collect(Collectors.toList());
        sidePots.add(new SidePot(amount, eligiblePlayerIds));
        previousLevel = level;
      }
      return sidePots;
    }

    private TableState state() {
      return state;
    }

    private TableState copyState() {
      return state;
    }
  }

  @FunctionalInterface
  private interface Task<T> {
    T run();
  }
}
