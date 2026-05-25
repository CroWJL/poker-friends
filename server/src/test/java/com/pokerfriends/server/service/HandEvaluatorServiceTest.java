package com.pokerfriends.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorServiceTest {

  @Test
  void shouldRankStraightFlushHigherThanFourOfKind() {
    HandEvaluatorService evaluator = new HandEvaluatorService();

    long straightFlush = evaluator.evaluateSevenCards(List.of("AH", "KH", "QH", "JH", "TH", "2C", "3D"));
    long fourOfKind = evaluator.evaluateSevenCards(List.of("AS", "AD", "AC", "AH", "KD", "QC", "2D"));

    assertTrue(straightFlush > fourOfKind);
  }

  @Test
  void shouldTreatWheelStraightAsValidStraight() {
    HandEvaluatorService evaluator = new HandEvaluatorService();

    long wheel = evaluator.evaluateSevenCards(List.of("AH", "2D", "3S", "4C", "5H", "KD", "QC"));
    long highCard = evaluator.evaluateSevenCards(List.of("AH", "KD", "QS", "9C", "7H", "3D", "2C"));

    assertTrue(wheel > highCard);
  }
}
