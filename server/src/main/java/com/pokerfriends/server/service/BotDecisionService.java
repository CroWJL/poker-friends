package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.ActionCommand;
import com.pokerfriends.server.model.PlayerActionType;
import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.model.TableState;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class BotDecisionService {
  public ActionCommand decide(TableState state, String botPlayerId) {
    PlayerState self = state.getPlayers().stream()
        .filter(player -> player.getPlayerId().equals(botPlayerId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Bot 玩家不存在"));
    if (!botPlayerId.equals(state.getActionPlayerId())) {
      throw new IllegalStateException("还没轮到该 Bot 行动");
    }

    int callAmount = Math.max(0, state.getCurrentBet() - self.getBetThisRound());
    boolean canCheck = callAmount == 0;
    boolean canCall = callAmount > 0 && self.getStack() >= callAmount;
    int minRaiseDelta = Math.max(1, state.getCurrentBet() == 0 ? 1 : state.getCurrentBet());
    int minRaiseTo = state.getCurrentBet() + minRaiseDelta;
    boolean canRaise = self.getStack() + self.getBetThisRound() > state.getCurrentBet() && self.getStack() > callAmount;
    ThreadLocalRandom random = ThreadLocalRandom.current();

    if (canCheck) {
      if (canRaise && random.nextDouble() < 0.1) {
        return new ActionCommand(PlayerActionType.RAISE, minRaiseTo);
      }
      return new ActionCommand(PlayerActionType.CHECK, null);
    }

    if (callAmount > 0 && self.getStack() <= callAmount) {
      if (random.nextDouble() < 0.35) {
        return new ActionCommand(PlayerActionType.FOLD, null);
      }
      return new ActionCommand(PlayerActionType.ALL_IN, null);
    }

    if (canCall) {
      if (random.nextDouble() < 0.22) {
        return new ActionCommand(PlayerActionType.FOLD, null);
      }
      if (canRaise && random.nextDouble() < 0.08) {
        return new ActionCommand(PlayerActionType.RAISE, minRaiseTo);
      }
      return new ActionCommand(PlayerActionType.CALL, null);
    }

    if (self.getStack() > 0) {
      return new ActionCommand(PlayerActionType.ALL_IN, null);
    }
    return new ActionCommand(PlayerActionType.FOLD, null);
  }
}
