package com.pokerfriends.server.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HandEvaluatorService {
  public record EvaluatedHand(long score, List<String> bestFiveCards, String handType) {
  }

  public long evaluateSevenCards(List<String> cards) {
    return evaluateBestHand(cards).score();
  }

  public EvaluatedHand evaluateBestHand(List<String> cards) {
    if (cards == null || cards.size() != 7) {
      throw new IllegalArgumentException("比牌需要 7 张牌");
    }
    EvaluatedHand best = new EvaluatedHand(Long.MIN_VALUE, List.of(), "");
    for (int a = 0; a < cards.size() - 4; a++) {
      for (int b = a + 1; b < cards.size() - 3; b++) {
        for (int c = b + 1; c < cards.size() - 2; c++) {
          for (int d = c + 1; d < cards.size() - 1; d++) {
            for (int e = d + 1; e < cards.size(); e++) {
              List<String> candidate = List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e));
              EvaluatedHand score = evaluateFiveCards(candidate);
              if (score.score() > best.score()) {
                best = score;
              }
            }
          }
        }
      }
    }
    return best;
  }

  private EvaluatedHand evaluateFiveCards(List<String> cards) {
    List<Integer> ranks = cards.stream().map(this::rankValue).sorted(Comparator.reverseOrder()).toList();
    List<Character> suits = cards.stream().map(this::suitValue).toList();
    boolean flush = suits.stream().distinct().count() == 1;
    Integer straightHigh = straightHigh(ranks);
    Map<Integer, Long> rankCounts = ranks.stream()
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    List<Map.Entry<Integer, Long>> byCountThenRank = rankCounts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<Integer, Long>, Long>comparing(Map.Entry::getValue).reversed()
            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
        .toList();

    if (flush && straightHigh != null) {
      return new EvaluatedHand(encode(8, List.of(straightHigh)), cards, "同花顺");
    }
    if (byCountThenRank.get(0).getValue() == 4) {
      int four = byCountThenRank.get(0).getKey();
      int kicker = byCountThenRank.get(1).getKey();
      return new EvaluatedHand(encode(7, List.of(four, kicker)), cards, "四条");
    }
    if (byCountThenRank.get(0).getValue() == 3 && byCountThenRank.get(1).getValue() == 2) {
      return new EvaluatedHand(
          encode(6, List.of(byCountThenRank.get(0).getKey(), byCountThenRank.get(1).getKey())),
          cards,
          "葫芦"
      );
    }
    if (flush) {
      return new EvaluatedHand(encode(5, ranks), cards, "同花");
    }
    if (straightHigh != null) {
      return new EvaluatedHand(encode(4, List.of(straightHigh)), cards, "顺子");
    }
    if (byCountThenRank.get(0).getValue() == 3) {
      int trips = byCountThenRank.get(0).getKey();
      List<Integer> kickers = byCountThenRank.stream()
          .filter(entry -> entry.getValue() == 1)
          .map(Map.Entry::getKey)
          .sorted(Comparator.reverseOrder())
          .toList();
      return new EvaluatedHand(encode(3, concat(List.of(trips), kickers)), cards, "三条");
    }
    if (byCountThenRank.get(0).getValue() == 2 && byCountThenRank.get(1).getValue() == 2) {
      int highPair = Math.max(byCountThenRank.get(0).getKey(), byCountThenRank.get(1).getKey());
      int lowPair = Math.min(byCountThenRank.get(0).getKey(), byCountThenRank.get(1).getKey());
      int kicker = byCountThenRank.stream()
          .filter(entry -> entry.getValue() == 1)
          .map(Map.Entry::getKey)
          .findFirst()
          .orElse(0);
      return new EvaluatedHand(encode(2, List.of(highPair, lowPair, kicker)), cards, "两对");
    }
    if (byCountThenRank.get(0).getValue() == 2) {
      int pair = byCountThenRank.get(0).getKey();
      List<Integer> kickers = byCountThenRank.stream()
          .filter(entry -> entry.getValue() == 1)
          .map(Map.Entry::getKey)
          .sorted(Comparator.reverseOrder())
          .toList();
      return new EvaluatedHand(encode(1, concat(List.of(pair), kickers)), cards, "一对");
    }
    return new EvaluatedHand(encode(0, ranks), cards, "高牌");
  }

  private Integer straightHigh(List<Integer> ranksDesc) {
    List<Integer> distinctDesc = ranksDesc.stream().distinct().toList();
    if (distinctDesc.size() < 5) {
      return null;
    }
    List<Integer> asc = distinctDesc.stream().sorted().toList();
    if (asc.equals(List.of(2, 3, 4, 5, 14))) {
      return 5;
    }
    for (int i = 1; i < asc.size(); i++) {
      if (asc.get(i) != asc.get(i - 1) + 1) {
        return null;
      }
    }
    return asc.get(asc.size() - 1);
  }

  private long encode(int category, List<Integer> tiebreakers) {
    long score = ((long) category) << 24;
    int shift = 20;
    for (int value : tiebreakers) {
      if (shift < 0) {
        break;
      }
      score |= ((long) value) << shift;
      shift -= 4;
    }
    return score;
  }

  private int rankValue(String card) {
    if (card == null || card.length() != 2) {
      throw new IllegalArgumentException("非法牌面: " + card);
    }
    return switch (card.charAt(0)) {
      case '2' -> 2;
      case '3' -> 3;
      case '4' -> 4;
      case '5' -> 5;
      case '6' -> 6;
      case '7' -> 7;
      case '8' -> 8;
      case '9' -> 9;
      case 'T' -> 10;
      case 'J' -> 11;
      case 'Q' -> 12;
      case 'K' -> 13;
      case 'A' -> 14;
      default -> throw new IllegalArgumentException("非法牌面: " + card);
    };
  }

  private char suitValue(String card) {
    if (card == null || card.length() != 2) {
      throw new IllegalArgumentException("非法牌面: " + card);
    }
    char suit = card.charAt(1);
    if (suit != 'C' && suit != 'D' && suit != 'H' && suit != 'S') {
      throw new IllegalArgumentException("非法牌面: " + card);
    }
    return suit;
  }

  private List<Integer> concat(List<Integer> prefix, List<Integer> suffix) {
    List<Integer> merged = new ArrayList<>(prefix);
    merged.addAll(suffix);
    return merged;
  }
}
