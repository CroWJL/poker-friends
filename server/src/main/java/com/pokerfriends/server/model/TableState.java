package com.pokerfriends.server.model;

import java.util.ArrayList;
import java.util.List;

public class TableState {
  private final String tableId;
  private String handId;
  private TableStage stage;
  private int pot;
  private int currentBet;
  private int actionCursor;
  private int dealerCursor;
  private int actionsInStage;
  private int playersToAct;
  private boolean started;
  private boolean practiceMode;
  private String practiceOutcome;
  private String dealerPlayerId;
  private String smallBlindPlayerId;
  private String bigBlindPlayerId;
  private final List<String> communityCards;
  private final List<String> remainingDeck;
  private final List<SidePot> sidePots;
  private final List<PotAward> potAwards;
  private final List<PlayerState> players;

  public TableState(String tableId) {
    this.tableId = tableId;
    this.handId = "h-001";
    this.stage = TableStage.WAITING;
    this.pot = 0;
    this.currentBet = 0;
    this.actionCursor = 0;
    this.dealerCursor = -1;
    this.actionsInStage = 0;
    this.playersToAct = 0;
    this.started = false;
    this.practiceMode = false;
    this.practiceOutcome = null;
    this.dealerPlayerId = null;
    this.smallBlindPlayerId = null;
    this.bigBlindPlayerId = null;
    this.communityCards = new ArrayList<>();
    this.remainingDeck = new ArrayList<>();
    this.sidePots = new ArrayList<>();
    this.potAwards = new ArrayList<>();
    this.players = new ArrayList<>();
  }

  public String getTableId() {
    return tableId;
  }

  public String getHandId() {
    return handId;
  }

  public void setHandId(String handId) {
    this.handId = handId;
  }

  public TableStage getStage() {
    return stage;
  }

  public void setStage(TableStage stage) {
    this.stage = stage;
  }

  public int getPot() {
    return pot;
  }

  public void setPot(int pot) {
    this.pot = pot;
  }

  public int getCurrentBet() {
    return currentBet;
  }

  public void setCurrentBet(int currentBet) {
    this.currentBet = currentBet;
  }

  public int getActionCursor() {
    return actionCursor;
  }

  public void setActionCursor(int actionCursor) {
    this.actionCursor = actionCursor;
  }

  public int getDealerCursor() {
    return dealerCursor;
  }

  public void setDealerCursor(int dealerCursor) {
    this.dealerCursor = dealerCursor;
  }

  public int getActionsInStage() {
    return actionsInStage;
  }

  public void setActionsInStage(int actionsInStage) {
    this.actionsInStage = actionsInStage;
  }

  public int getPlayersToAct() {
    return playersToAct;
  }

  public void setPlayersToAct(int playersToAct) {
    this.playersToAct = playersToAct;
  }

  public boolean isStarted() {
    return started;
  }

  public void setStarted(boolean started) {
    this.started = started;
  }

  public boolean isPracticeMode() {
    return practiceMode;
  }

  public void setPracticeMode(boolean practiceMode) {
    this.practiceMode = practiceMode;
  }

  public String getPracticeOutcome() {
    return practiceOutcome;
  }

  public void setPracticeOutcome(String practiceOutcome) {
    this.practiceOutcome = practiceOutcome;
  }

  public String getDealerPlayerId() {
    return dealerPlayerId;
  }

  public void setDealerPlayerId(String dealerPlayerId) {
    this.dealerPlayerId = dealerPlayerId;
  }

  public String getSmallBlindPlayerId() {
    return smallBlindPlayerId;
  }

  public void setSmallBlindPlayerId(String smallBlindPlayerId) {
    this.smallBlindPlayerId = smallBlindPlayerId;
  }

  public String getBigBlindPlayerId() {
    return bigBlindPlayerId;
  }

  public void setBigBlindPlayerId(String bigBlindPlayerId) {
    this.bigBlindPlayerId = bigBlindPlayerId;
  }

  public List<String> getCommunityCards() {
    return communityCards;
  }

  public List<String> getRemainingDeck() {
    return remainingDeck;
  }

  public void setRemainingDeck(List<String> remainingDeck) {
    this.remainingDeck.clear();
    this.remainingDeck.addAll(remainingDeck);
  }

  public List<SidePot> getSidePots() {
    return sidePots;
  }

  public void setSidePots(List<SidePot> sidePots) {
    this.sidePots.clear();
    this.sidePots.addAll(sidePots);
  }

  public List<PotAward> getPotAwards() {
    return potAwards;
  }

  public void setPotAwards(List<PotAward> potAwards) {
    this.potAwards.clear();
    this.potAwards.addAll(potAwards);
  }

  public List<PlayerState> getPlayers() {
    return players;
  }

  public String getActionPlayerId() {
    if (players.isEmpty() || actionCursor < 0 || actionCursor >= players.size()) {
      return null;
    }
    return players.get(actionCursor).getPlayerId();
  }
}
