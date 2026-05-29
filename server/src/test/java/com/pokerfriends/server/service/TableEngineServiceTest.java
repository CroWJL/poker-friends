package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.model.PlayerActionType;
import com.pokerfriends.server.model.SidePot;
import com.pokerfriends.server.model.TableStage;
import com.pokerfriends.server.model.TableState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableEngineServiceTest {

  @Test
  void shouldStayWaitingUntilHostStartsGame() {
    TableEngineService service = createHeadsUpTableWithoutStart();

    TableState snapshot = service.getSnapshot("t-1");
    assertEquals(2, snapshot.getPlayers().size());
    assertEquals(TableStage.WAITING, snapshot.getStage());
    assertEquals(0, snapshot.getPot());
    assertEquals(0, snapshot.getCurrentBet());
  }

  @Test
  void shouldRejectStartGameFromNonHost() {
    TableEngineService service = createHeadsUpTableWithoutStart();

    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> service.startGame("t-1", "p-2").join()
    );
    assertEquals("仅房主可开始牌局", exception.getCause().getMessage());
  }

  @Test
  void shouldRejectActionWhenNotCurrentPlayer() {
    TableEngineService service = createHeadsUpTable();

    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> service.submitAction("t-1", "p-2", new ActionCommand(PlayerActionType.CHECK, null)).join()
    );
    assertEquals("还没轮到你行动", exception.getCause().getMessage());
  }

  @Test
  void shouldRejectRaiseNotHigherThanCurrentBet() {
    TableEngineService service = createHeadsUpTable();

    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.RAISE, 20)).join()
    );
    assertEquals("raise 金额必须大于当前注", exception.getCause().getMessage());
  }

  @Test
  void shouldRejectCheckWhenPlayerHasNotMatchedCurrentBet() {
    TableEngineService service = createHeadsUpTable();

    CompletionException exception = assertThrows(
        CompletionException.class,
        () -> service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.CHECK, null)).join()
    );
    assertEquals("当前不能 check", exception.getCause().getMessage());
  }

  @Test
  void shouldUpdateCurrentBetWhenPlayerGoesAllInAboveCurrentBet() {
    TableEngineService service = createHeadsUpTable();

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    TableState snapshot = service.getSnapshot("t-1");
    assertEquals(1010, snapshot.getPot());
    assertEquals(1010, snapshot.getCurrentBet());
    assertEquals(0, snapshot.getPlayers().get(0).getStack());
  }

  @Test
  void shouldWaitForOpponentActionAfterSinglePlayerAllIn() {
    TableEngineService service = createHeadsUpTable();

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    TableState snapshot = service.getSnapshot("t-1");

    assertEquals(TableStage.PREFLOP, snapshot.getStage());
    assertEquals("p-2", snapshot.getActionPlayerId());
    assertTrue(snapshot.getPotAwards().isEmpty());
  }

  @Test
  void shouldAdvanceToFlopAfterCallAndCheck() {
    TableEngineService service = createHeadsUpTable();

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.CALL, null)).join();
    TableState afterCall = service.getSnapshot("t-1");
    assertEquals(40, afterCall.getPot());
    assertEquals(1, afterCall.getActionCursor());

    service.submitAction("t-1", "p-2", new ActionCommand(PlayerActionType.CHECK, null)).join();
    TableState afterCheck = service.getSnapshot("t-1");
    assertEquals(TableStage.FLOP, afterCheck.getStage());
    assertEquals(0, afterCheck.getCurrentBet());
    assertEquals(3, afterCheck.getCommunityCards().size());
    assertTrue(afterCheck.getCommunityCards().stream().noneMatch(card -> "??".equals(card)));
  }

  @Test
  void shouldDealTwoHoleCardsForEachPlayerAtHandStart() {
    TableEngineService service = createHeadsUpTable();

    TableState snapshot = service.getSnapshot("t-1");
    assertEquals(2, snapshot.getPlayers().size());
    assertTrue(snapshot.getPlayers().stream().allMatch(player -> player.getHoleCards().size() == 2));
  }

  @Test
  void shouldRequirePlayersToRespondAfterRaiseBeforeAdvancingStage() {
    TableEngineService service = createThreePlayersTable();

    service.submitAction("t-3", "p-1", new ActionCommand(PlayerActionType.CALL, null)).join();
    service.submitAction("t-3", "p-2", new ActionCommand(PlayerActionType.RAISE, 40)).join();
    service.submitAction("t-3", "p-3", new ActionCommand(PlayerActionType.CALL, null)).join();

    TableState beforeLastCallerActs = service.getSnapshot("t-3");
    assertEquals(TableStage.PREFLOP, beforeLastCallerActs.getStage());
    assertEquals("p-1", beforeLastCallerActs.getActionPlayerId());

    service.submitAction("t-3", "p-1", new ActionCommand(PlayerActionType.CALL, null)).join();
    TableState afterLastCallerActs = service.getSnapshot("t-3");
    assertEquals(TableStage.FLOP, afterLastCallerActs.getStage());
  }

  @Test
  void shouldBuildMainPotAndSidePotAfterDifferentStacksAllIn() {
    TableEngineService service = createFourPlayersTableWithoutBlind("t-4");
    TableState state = service.getSnapshot("t-4");
    state.getPlayers().get(0).setStack(100);
    state.getPlayers().get(1).setStack(200);
    state.getPlayers().get(2).setStack(300);
    state.getPlayers().get(3).setStack(1000);

    service.submitAction("t-4", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    service.submitAction("t-4", "p-2", new ActionCommand(PlayerActionType.CALL, null)).join();
    service.submitAction("t-4", "p-3", new ActionCommand(PlayerActionType.CALL, null)).join();
    service.submitAction("t-4", "p-4", new ActionCommand(PlayerActionType.CALL, null)).join();
    service.submitAction("t-4", "p-2", new ActionCommand(PlayerActionType.ALL_IN, null)).join();

    TableState snapshot = service.getSnapshot("t-4");
    assertEquals(2, snapshot.getSidePots().size());

    SidePot mainPot = snapshot.getSidePots().get(0);
    assertEquals(400, mainPot.amount());
    assertEquals(List.of("p-1", "p-2", "p-3", "p-4"), mainPot.eligiblePlayerIds());

    SidePot sidePot = snapshot.getSidePots().get(1);
    assertEquals(300, sidePot.amount());
    assertEquals(List.of("p-2", "p-3", "p-4"), sidePot.eligiblePlayerIds());
  }

  @Test
  void shouldFinishHandWhenCurrentPlayerFoldsHeadsUp() {
    TableEngineService service = createHeadsUpTable();

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.FOLD, null)).join();
    TableState snapshot = service.getSnapshot("t-1");
    assertEquals(TableStage.FINISHED, snapshot.getStage());
    assertEquals(0, snapshot.getPot());
    assertEquals(0, snapshot.getCurrentBet());
    assertEquals("p-2", snapshot.getPotAwards().get(0).playerId());
  }

  @Test
  void shouldRunOutAndSettleWhenNoPlayersCanAct() {
    TableEngineService service = createHeadsUpTable();

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    service.submitAction("t-1", "p-2", new ActionCommand(PlayerActionType.CALL, null)).join();

    TableState snapshot = service.getSnapshot("t-1");
    assertEquals(TableStage.FINISHED, snapshot.getStage());
    assertEquals(0, snapshot.getPot());
    assertEquals(0, snapshot.getSidePots().size());
    assertEquals(2000, totalStack(snapshot));
  }

  @Test
  void shouldConserveTotalStackAfterSettlement() {
    TableEngineService service = createHeadsUpTable();
    int before = totalStack(service.getSnapshot("t-1"));

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.CALL, null)).join();
    service.submitAction("t-1", "p-2", new ActionCommand(PlayerActionType.CHECK, null)).join();
    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    service.submitAction("t-1", "p-2", new ActionCommand(PlayerActionType.CALL, null)).join();

    int after = totalStack(service.getSnapshot("t-1"));
    assertEquals(before, after);
  }

  @Test
  void shouldAwardPotByRealHandStrengthAtShowdown() {
    FixedDeckService fixedDeckService = new FixedDeckService(List.of(
        "6C", "7D", "8H", "9S", "TC", "JD", "QH", "KS",
        "7C", "5S", "4H", "3D", "2C", "KD", "KC", "AD", "AH"
    ));
    TableEngineService service = new TableEngineService(fixedDeckService, new HandEvaluatorService(), new BotNameService());
    service.initTable("t-showdown", 10, 20, 6);
    service.addPlayer("t-showdown", "p-1", "Alice");
    service.addPlayer("t-showdown", "p-2", "Bob");
    service.startGame("t-showdown", "p-1").join();

    // 固定牌序下，p-1 手牌为 AH AD，p-2 手牌为 KC KD，公共牌为 2C 3D 4H 5S 7C。
    service.submitAction("t-showdown", "p-1", new ActionCommand(PlayerActionType.ALL_IN, null)).join();
    service.submitAction("t-showdown", "p-2", new ActionCommand(PlayerActionType.CALL, null)).join();

    TableState snapshot = service.getSnapshot("t-showdown");
    assertEquals(TableStage.FINISHED, snapshot.getStage());
    assertEquals("p-1", snapshot.getPotAwards().get(0).playerId());
    assertEquals(5, snapshot.getPotAwards().get(0).bestFiveCards().size());
    assertTrue(!snapshot.getPotAwards().get(0).handType().isBlank());
    assertEquals(2000, snapshot.getPlayers().stream()
        .filter(player -> "p-1".equals(player.getPlayerId()))
        .findFirst()
        .orElseThrow()
        .getStack());
  }

  @Test
  void shouldRotateDealerAndBlindsForNextHand() {
    TableEngineService service = createHeadsUpTable();
    TableState firstHand = service.getSnapshot("t-1");
    assertEquals("p-1", firstHand.getDealerPlayerId());
    assertEquals("p-1", firstHand.getSmallBlindPlayerId());
    assertEquals("p-2", firstHand.getBigBlindPlayerId());
    assertEquals(10, firstHand.getPlayers().get(0).getBetThisRound());
    assertEquals(20, firstHand.getPlayers().get(1).getBetThisRound());

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.FOLD, null)).join();
    service.startGame("t-1", "p-1").join();
    TableState secondHand = service.getSnapshot("t-1");

    assertEquals(TableStage.PREFLOP, secondHand.getStage());
    assertEquals("p-2", secondHand.getDealerPlayerId());
    assertEquals("p-2", secondHand.getSmallBlindPlayerId());
    assertEquals("p-1", secondHand.getBigBlindPlayerId());
    assertEquals(20, secondHand.getPlayers().get(0).getBetThisRound());
    assertEquals(10, secondHand.getPlayers().get(1).getBetThisRound());
    assertEquals("p-2", secondHand.getActionPlayerId());
  }

  @Test
  void shouldPutNewJoinerIntoWaitingAndAutoJoinNextHand() {
    TableEngineService service = createHeadsUpTable();

    service.addPlayer("t-1", "p-3", "Carol");
    TableState duringHand = service.getSnapshot("t-1");
    assertTrue(duringHand.getPlayers().stream()
        .filter(player -> "p-3".equals(player.getPlayerId()))
        .findFirst()
        .orElseThrow()
        .isWaitingForNextHand());

    service.submitAction("t-1", "p-1", new ActionCommand(PlayerActionType.FOLD, null)).join();
    service.startGame("t-1", "p-1").join();
    TableState nextHand = service.getSnapshot("t-1");
    assertEquals(TableStage.PREFLOP, nextHand.getStage());
    assertTrue(nextHand.getPlayers().stream()
        .filter(player -> "p-3".equals(player.getPlayerId()))
        .findFirst()
        .orElseThrow()
        .isInHand());
    assertEquals(3, nextHand.getPlayers().stream().filter(player -> player.getHoleCards().size() == 2).count());
  }

  private TableEngineService createHeadsUpTable() {
    TableEngineService service = createHeadsUpTableWithoutStart();
    service.startGame("t-1", "p-1").join();
    return service;
  }

  private TableEngineService createHeadsUpTableWithoutStart() {
    TableEngineService service = new TableEngineService();
    service.initTable("t-1", 10, 20, 6);
    service.addPlayer("t-1", "p-1", "Alice");
    service.addPlayer("t-1", "p-2", "Bob");
    return service;
  }

  private TableEngineService createThreePlayersTable() {
    return createThreePlayersTableWithoutBlind("t-3");
  }

  private TableEngineService createThreePlayersTableWithoutBlind(String tableId) {
    TableEngineService service = new TableEngineService();
    service.initTable(tableId, 0, 0, 6);
    service.addPlayer(tableId, "p-1", "Alice");
    service.addPlayer(tableId, "p-2", "Bob");
    service.addPlayer(tableId, "p-3", "Carol");
    service.startGame(tableId, "p-1").join();
    return service;
  }

  private TableEngineService createFourPlayersTableWithoutBlind(String tableId) {
    TableEngineService service = createThreePlayersTableWithoutBlind(tableId);
    service.addPlayer(tableId, "p-4", "Dave");
    return service;
  }

  private int totalStack(TableState state) {
    return state.getPlayers().stream().mapToInt(player -> player.getStack()).sum() + state.getPot();
  }

  private static final class FixedDeckService extends DeckService {
    private final List<String> deck;

    private FixedDeckService(List<String> deck) {
      this.deck = deck;
    }

    @Override
    public List<String> shuffledDeck() {
      return deck;
    }
  }
}
