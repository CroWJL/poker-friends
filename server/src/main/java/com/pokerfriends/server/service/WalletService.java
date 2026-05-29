package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.WalletTransactionResponse;
import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.model.WalletTransactionType;
import com.pokerfriends.server.persistence.RoomPlayerEntity;
import com.pokerfriends.server.persistence.RoomPlayerRepository;
import com.pokerfriends.server.persistence.UserEntity;
import com.pokerfriends.server.persistence.UserRepository;
import com.pokerfriends.server.persistence.WalletTransactionEntity;
import com.pokerfriends.server.persistence.WalletTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {
  public static final int INITIAL_BALANCE = 5000;
  public static final int TABLE_BUY_IN = 1000;

  private final UserRepository userRepository;
  private final RoomPlayerRepository roomPlayerRepository;
  private final WalletTransactionRepository walletTransactionRepository;

  public WalletService(
      UserRepository userRepository,
      RoomPlayerRepository roomPlayerRepository,
      WalletTransactionRepository walletTransactionRepository
  ) {
    this.userRepository = userRepository;
    this.roomPlayerRepository = roomPlayerRepository;
    this.walletTransactionRepository = walletTransactionRepository;
  }

  public int getBalance(String userId) {
    return requireUser(userId).getWalletBalance();
  }

  public List<WalletTransactionResponse> listRecentTransactions(String userId, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public int buyIn(String userId) {
    return buyIn(userId, WalletTransactionType.TABLE_BUY_IN, null, null, null);
  }

  @Transactional
  public int buyIn(
      String userId,
      WalletTransactionType type,
      String roomId,
      String tableId,
      String playerId
  ) {
    UserEntity user = requireUser(userId);
    int balanceAfter = user.getWalletBalance() - TABLE_BUY_IN;
    user.setWalletBalance(balanceAfter);
    userRepository.save(user);
    recordTransaction(userId, type, -TABLE_BUY_IN, balanceAfter, roomId, tableId, playerId);
    return balanceAfter;
  }

  @Transactional
  public int cashOut(String userId, int amount) {
    return cashOut(userId, amount, null, null, null);
  }

  @Transactional
  public int cashOut(String userId, int amount, String roomId, String tableId, String playerId) {
    if (amount <= 0) {
      return getBalance(userId);
    }
    UserEntity user = requireUser(userId);
    int balanceAfter = user.getWalletBalance() + amount;
    user.setWalletBalance(balanceAfter);
    userRepository.save(user);
    recordTransaction(userId, WalletTransactionType.TABLE_CASH_OUT, amount, balanceAfter, roomId, tableId, playerId);
    return balanceAfter;
  }

  @Transactional
  public boolean tryAutoRebuy(String roomId, String playerId, PlayerState player) {
    if (player.getStack() > 0) {
      return false;
    }
    if (BotOrchestratorService.isBotPlayerId(playerId)) {
      return false;
    }
    RoomPlayerEntity mapping = roomPlayerRepository.findByRoomIdAndPlayerId(roomId, playerId).orElse(null);
    if (mapping == null) {
      return false;
    }
    String tableId = tableIdFromRoomId(roomId);
    buyIn(mapping.getUserId(), WalletTransactionType.AUTO_REBUY, roomId, tableId, playerId);
    player.setStack(TABLE_BUY_IN);
    return true;
  }

  public static String roomIdFromTableId(String tableId) {
    if (tableId != null && tableId.startsWith("table-")) {
      return tableId.substring("table-".length());
    }
    return null;
  }

  public static String tableIdFromRoomId(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      return null;
    }
    return "table-" + roomId;
  }

  private void recordTransaction(
      String userId,
      WalletTransactionType type,
      int amount,
      int balanceAfter,
      String roomId,
      String tableId,
      String playerId
  ) {
    walletTransactionRepository.save(new WalletTransactionEntity(
        userId,
        type,
        amount,
        balanceAfter,
        roomId,
        tableId,
        playerId
    ));
  }

  private WalletTransactionResponse toResponse(WalletTransactionEntity entity) {
    return new WalletTransactionResponse(
        entity.getId(),
        entity.getType(),
        entity.getAmount(),
        entity.getBalanceAfter(),
        entity.getRoomId(),
        entity.getTableId(),
        entity.getPlayerId(),
        entity.getCreatedAt()
    );
  }

  private UserEntity requireUser(String userId) {
    return userRepository.findByUserId(userId)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
  }
}
