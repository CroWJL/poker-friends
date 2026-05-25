package com.pokerfriends.server.model;

import java.util.ArrayList;
import java.util.List;

public class PlayerState {
  private final String playerId;
  private final String playerName;
  private final int seat;
  private int stack;
  private boolean inHand;
  private boolean waitingForNextHand;
  private int betThisRound;
  private int totalCommitted;
  private final List<String> holeCards;

  public PlayerState(String playerId, String playerName, int seat, int stack) {
    this.playerId = playerId;
    this.playerName = playerName;
    this.seat = seat;
    this.stack = stack;
    this.inHand = true;
    this.waitingForNextHand = false;
    this.betThisRound = 0;
    this.totalCommitted = 0;
    this.holeCards = new ArrayList<>();
  }

  public String getPlayerId() {
    return playerId;
  }

  public String getPlayerName() {
    return playerName;
  }

  public int getSeat() {
    return seat;
  }

  public int getStack() {
    return stack;
  }

  public void setStack(int stack) {
    this.stack = stack;
  }

  public boolean isInHand() {
    return inHand;
  }

  public void setInHand(boolean inHand) {
    this.inHand = inHand;
  }

  public boolean isWaitingForNextHand() {
    return waitingForNextHand;
  }

  public void setWaitingForNextHand(boolean waitingForNextHand) {
    this.waitingForNextHand = waitingForNextHand;
  }

  public int getBetThisRound() {
    return betThisRound;
  }

  public void setBetThisRound(int betThisRound) {
    this.betThisRound = betThisRound;
  }

  public int getTotalCommitted() {
    return totalCommitted;
  }

  public void setTotalCommitted(int totalCommitted) {
    this.totalCommitted = totalCommitted;
  }

  public List<String> getHoleCards() {
    return holeCards;
  }

  public void setHoleCards(List<String> holeCards) {
    this.holeCards.clear();
    this.holeCards.addAll(holeCards);
  }
}
