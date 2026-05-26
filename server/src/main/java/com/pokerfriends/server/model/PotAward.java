package com.pokerfriends.server.model;

import java.util.List;

public record PotAward(String playerId, int amount, List<String> bestFiveCards, String handType) {
  public PotAward(String playerId, int amount) {
    this(playerId, amount, List.of(), "");
  }
}
