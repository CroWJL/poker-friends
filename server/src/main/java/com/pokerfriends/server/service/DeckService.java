package com.pokerfriends.server.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeckService {
  private static final List<Character> RANKS = List.of(
      '2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'A'
  );
  private static final List<Character> SUITS = List.of('C', 'D', 'H', 'S');

  public List<String> shuffledDeck() {
    List<String> deck = new ArrayList<>(52);
    for (char suit : SUITS) {
      for (char rank : RANKS) {
        deck.add("" + rank + suit);
      }
    }
    Collections.shuffle(deck, ThreadLocalRandom.current());
    return deck;
  }
}
