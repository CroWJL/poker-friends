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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class TableEngineService {
  public static final int PRACTICE_HUMAN_STACK = 1000;
  public static final int PRACTICE_BOT_STACK = 500;
  public static final int PRACTICE_BOT_COUNT = 7;

  private final Map<String, TableActor> tables = new ConcurrentHashMap<>();
  private final DeckService deckService;
  private final HandEvaluatorService handEvaluatorService;
  private final BotNameService botNameService;

  TableEngineService() {
    this(new DeckService(), new HandEvaluatorService(), new BotNameService());
  }

  @Autowired
  public TableEngineService(
      DeckService deckService,
      HandEvaluatorService handEvaluatorService,
      BotNameService botNameService
  ) {
    this.deckService = deckService;
    this.handEvaluatorService = handEvaluatorService;
    this.botNameService = botNameService;
  }

  public void initTable(String tableId, int smallBlind, int bigBlind, int maxPlayers) {
    tables.computeIfAbsent(tableId, id -> new TableActor(
        id, smallBlind, bigBlind, maxPlayers, false, deckService, handEvaluatorService
    ));
  }

  public void initPracticeTable(String tableId, int smallBlind, int bigBlind, int maxPlayers) {
    tables.computeIfAbsent(tableId, id -> new TableActor(
        id, smallBlind, bigBlind, maxPlayers, true, deckService, handEvaluatorService
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
        snapshot.isPracticeMode(),
        deckService,
        handEvaluatorService,
        snapshot
    ));
  }

  public void addPlayer(String tableId, String playerId, String playerName) {
    addPlayer(tableId, playerId, playerName, 1000);
  }

  public void addPlayer(String tableId, String playerId, String playerName, int initialStack) {
    TableActor actor = getActor(tableId);
    actor.run(() -> {
      actor.addPlayer(playerId, playerName, initialStack);
      return null;
    }).join();
  }

  public CompletableFuture<TableState> acknowledgePracticeOutcome(String tableId, String playerId) {
    List<String> botNames = botNameService.randomBotNames(PRACTICE_BOT_COUNT);
    return getActor(tableId).run(() -> {
      getActor(tableId).acknowledgePracticeOutcome(playerId, botNames);
      return getActor(tableId).state();
    });
  }

  public List<String> randomPracticeBotNames() {
    return botNameService.randomBotNames(PRACTICE_BOT_COUNT);
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

  public CompletableFuture<TableState> startGame(String tableId, String playerId) {
    return getActor(tableId).run(() -> {
      getActor(tableId).startGame(playerId);
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
        boolean practiceMode,
        DeckService deckService,
        HandEvaluatorService handEvaluatorService
    ) {
      this.executor = Executors.newSingleThreadExecutor();
      this.state = new TableState(tableId);
      this.state.setPracticeMode(practiceMode);
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
        boolean practiceMode,
        DeckService deckService,
        HandEvaluatorService handEvaluatorService,
        TableState snapshot
    ) {
      this.executor = Executors.newSingleThreadExecutor();
      this.state = snapshot;
      if (!this.state.isPracticeMode()) {
        this.state.setPracticeMode(practiceMode);
      }
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

    private void addPlayer(String playerId, String playerName, int initialStack) {
      if (state.getPlayers().size() >= maxPlayers) {
        throw new IllegalStateException("牌桌人数已满");
      }
      int nextSeat = state.getPlayers().size() + 1;
      PlayerState joinedPlayer = new PlayerState(playerId, playerName, nextSeat, initialStack);
      boolean waiting = true;
      joinedPlayer.setWaitingForNextHand(waiting);
      joinedPlayer.setInHand(!waiting);
      state.getPlayers().add(joinedPlayer);
      state.getPlayers().sort(Comparator.comparingInt(PlayerState::getSeat));
      if (state.isStarted() && state.getStage() == TableStage.WAITING && countPlayersWithChips() >= 2) {
        startHandWithRotatingBlinds();
      } else if (!state.isStarted() || state.getStage() == TableStage.WAITING) {
        enterWaitingState(false);
      }
    }

    private void startGame(String playerId) {
      PlayerState player = state.getPlayers().stream()
          .filter(candidate -> candidate.getPlayerId().equals(playerId))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("玩家不存在"));
      if (player.getSeat() != 1) {
        throw new IllegalStateException("仅房主可开始牌局");
      }
      if (state.isPracticeMode() && state.getPracticeOutcome() != null && !state.getPracticeOutcome().isBlank()) {
        throw new IllegalStateException("请先确认练习结果");
      }
      if (countPlayersWithChips() < 2) {
        throw new IllegalStateException("人数不足 2 人，无法开始");
      }
      state.setStarted(true);
      if (state.getStage() == TableStage.WAITING || state.getStage() == TableStage.FINISHED) {
        startHandWithRotatingBlinds();
      }
    }

    private void startHandWithRotatingBlinds() {
      if (!state.isStarted() || countPlayersWithChips() < 2) {
        enterWaitingState(false);
        return;
      }
      startNewHand();
      List<Integer> eligibleSeats = eligibleSeatIndexes();
      rotateDealer(eligibleSeats);
      int sbIndex = eligibleSeats.size() == 2 ? dealerCursor : nextEligibleIndex(dealerCursor, eligibleSeats);
      int bbIndex = nextEligibleIndex(sbIndex, eligibleSeats);
      PlayerState dealer = state.getPlayers().get(dealerCursor);
      PlayerState sb = state.getPlayers().get(sbIndex);
      PlayerState bb = state.getPlayers().get(bbIndex);
      state.setDealerPlayerId(dealer.getPlayerId());
      state.setSmallBlindPlayerId(sb.getPlayerId());
      state.setBigBlindPlayerId(bb.getPlayerId());
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
      if (state.getStage() == TableStage.WAITING || state.getStage() == TableStage.FINISHED) {
        throw new IllegalStateException("当前牌局未开始");
      }
      // 新一局开始后，首个动作发生时清空上一局的派奖展示，避免旧结果长期残留。
      if (state.getStage() == TableStage.PREFLOP && state.getActionsInStage() == 0 && !state.getPotAwards().isEmpty()) {
        state.setPotAwards(List.of());
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
      if (playersCanAct <= 1 && !hasPendingActionPlayer()) {
        runOutBoardAndSettle();
        return;
      }
      PlayerState actorAfterAction = state.getPlayers().get(state.getActionCursor());
      boolean actorCanKeepActing = actorAfterAction.isInHand() && actorAfterAction.getStack() > 0;
      if (raised) {
        state.setPlayersToAct(Math.max(0, playersCanAct - (actorCanKeepActing ? 1 : 0)));
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

    private boolean hasPendingActionPlayer() {
      return state.getPlayers().stream()
          .anyMatch(player ->
              player.isInHand()
                  && player.getStack() > 0
                  && player.getBetThisRound() < state.getCurrentBet()
          );
    }

    private void advanceStage() {
      state.setActionsInStage(0);
      state.getPlayers().forEach(player -> player.setBetThisRound(0));
      state.setCurrentBet(0);
      switch (state.getStage()) {
        case WAITING -> {
        }
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
        state.setPotAwards(List.of(new PotAward(winner.getPlayerId(), state.getPot(), List.of(), "未摊牌获胜")));
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
      Map<String, HandEvaluatorService.EvaluatedHand> evaluatedHands = evaluateHandsForActivePlayers();
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
            .mapToLong(player -> evaluatedHands.getOrDefault(
                player.getPlayerId(),
                new HandEvaluatorService.EvaluatedHand(Long.MIN_VALUE, List.of(), "")
            ).score())
            .max()
            .orElse(Long.MIN_VALUE);
        List<PlayerState> winners = contenders.stream()
            .filter(player -> evaluatedHands.getOrDefault(
                player.getPlayerId(),
                new HandEvaluatorService.EvaluatedHand(Long.MIN_VALUE, List.of(), "")
            ).score() == bestScore)
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
          .map(entry -> {
            HandEvaluatorService.EvaluatedHand result = evaluatedHands.get(entry.getKey());
            if (result == null) {
              return new PotAward(entry.getKey(), entry.getValue(), List.of(), "");
            }
            return new PotAward(entry.getKey(), entry.getValue(), result.bestFiveCards(), result.handType());
          })
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
      if (state.isPracticeMode()) {
        applyPracticePostHandRules();
      }
    }

    private void applyPracticePostHandRules() {
      state.getPlayers().removeIf(player ->
          BotOrchestratorService.isBotPlayerId(player.getPlayerId()) && player.getStack() <= 0
      );

      PlayerState human = findHumanPlayer();
      if (human == null || human.getStack() <= 0) {
        state.setPracticeOutcome("LOSE");
        return;
      }
      boolean anyBotRemaining = state.getPlayers().stream()
          .anyMatch(player -> BotOrchestratorService.isBotPlayerId(player.getPlayerId()) && player.getStack() > 0);
      if (!anyBotRemaining) {
        state.setPracticeOutcome("WIN");
      }
    }

    private PlayerState findHumanPlayer() {
      return state.getPlayers().stream()
          .filter(player -> !BotOrchestratorService.isBotPlayerId(player.getPlayerId()))
          .findFirst()
          .orElse(null);
    }

    private void acknowledgePracticeOutcome(String playerId, List<String> botNames) {
      if (!state.isPracticeMode()) {
        throw new IllegalStateException("非人机练习桌");
      }
      if (state.getPracticeOutcome() == null || state.getPracticeOutcome().isBlank()) {
        throw new IllegalStateException("当前无需确认练习结果");
      }
      PlayerState host = state.getPlayers().stream()
          .filter(candidate -> candidate.getPlayerId().equals(playerId))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("玩家不存在"));
      if (host.getSeat() != 1) {
        throw new IllegalStateException("仅房主可确认");
      }
      String humanId = host.getPlayerId();
      String humanName = host.getPlayerName();
      state.getPlayers().clear();
      state.setPracticeOutcome(null);
      state.setStarted(false);
      dealerCursor = -1;
      addPlayer(humanId, humanName, PRACTICE_HUMAN_STACK);
      for (int botIndex = 1; botIndex <= PRACTICE_BOT_COUNT; botIndex++) {
        String botName = botIndex <= botNames.size() ? botNames.get(botIndex - 1) : "Bot-" + botIndex;
        addPlayer(
            BotOrchestratorService.BOT_PLAYER_ID_PREFIX + botIndex,
            botName,
            PRACTICE_BOT_STACK
        );
      }
    }

    private boolean isBeforePreflopFirstAction() {
      return state.getStage() == TableStage.PREFLOP
          && state.getActionsInStage() == 0
          && state.getCommunityCards().isEmpty()
          && !deck.isEmpty();
    }

    private void enterWaitingState(boolean keepAwards) {
      state.setStage(TableStage.WAITING);
      state.setPot(0);
      state.setCurrentBet(0);
      state.setActionsInStage(0);
      state.setPlayersToAct(0);
      state.getCommunityCards().clear();
      state.setSidePots(List.of());
      if (!keepAwards) {
        state.setPotAwards(List.of());
      }
      state.setDealerPlayerId(null);
      state.setSmallBlindPlayerId(null);
      state.setBigBlindPlayerId(null);
      deck = List.of();
      syncRemainingDeck();
      for (PlayerState player : state.getPlayers()) {
        player.setInHand(false);
        player.setWaitingForNextHand(true);
        player.setBetThisRound(0);
        player.setTotalCommitted(0);
        player.setHoleCards(List.of());
      }
    }

    private void startNewHand() {
      state.setStage(TableStage.PREFLOP);
      state.setHandId(nextHandId(state.getHandId()));
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

    private String nextHandId(String current) {
      if (current == null || current.isBlank()) {
        return "h-001";
      }
      int dash = current.lastIndexOf('-');
      if (dash < 0 || dash == current.length() - 1) {
        return "h-001";
      }
      try {
        int number = Integer.parseInt(current.substring(dash + 1));
        return "h-%03d".formatted(number + 1);
      } catch (NumberFormatException ignored) {
        return "h-001";
      }
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

    private Map<String, HandEvaluatorService.EvaluatedHand> evaluateHandsForActivePlayers() {
      return state.getPlayers().stream()
          .filter(PlayerState::isInHand)
          .filter(player -> player.getHoleCards().size() == 2)
          .collect(Collectors.toMap(
              PlayerState::getPlayerId,
              player -> handEvaluatorService.evaluateBestHand(mergeCards(player.getHoleCards(), state.getCommunityCards())),
              (left, right) -> left,
              LinkedHashMap::new
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
          && state.getStage() != TableStage.WAITING
          && (state.getPot() > 0 || state.getActionsInStage() > 0 || !state.getCommunityCards().isEmpty());
    }

    private void rotateDealer(List<Integer> eligibleSeats) {
      if (eligibleSeats.isEmpty()) {
        dealerCursor = -1;
        state.setDealerCursor(-1);
        state.setDealerPlayerId(null);
        state.setSmallBlindPlayerId(null);
        state.setBigBlindPlayerId(null);
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
