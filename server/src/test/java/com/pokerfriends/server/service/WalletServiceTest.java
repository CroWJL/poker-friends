package com.pokerfriends.server.service;

import com.pokerfriends.server.model.PlayerState;
import com.pokerfriends.server.persistence.RoomPlayerEntity;
import com.pokerfriends.server.persistence.RoomPlayerRepository;
import com.pokerfriends.server.persistence.UserEntity;
import com.pokerfriends.server.persistence.UserRepository;
import com.pokerfriends.server.persistence.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
  @Mock
  private UserRepository userRepository;
  @Mock
  private RoomPlayerRepository roomPlayerRepository;
  @Mock
  private WalletTransactionRepository walletTransactionRepository;

  private WalletService walletService;

  @BeforeEach
  void setUp() {
    walletService = new WalletService(userRepository, roomPlayerRepository, walletTransactionRepository);
  }

  @Test
  void buyInShouldDeductTableBuyInAndAllowNegativeBalance() {
    UserEntity user = new UserEntity("u-1", "Alice");
    user.setWalletBalance(500);
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    int balance = walletService.buyIn("u-1");

    assertEquals(-500, balance);
    verify(userRepository).save(user);
    verify(walletTransactionRepository).save(any());
  }

  @Test
  void cashOutShouldReturnRemainingStackToWallet() {
    UserEntity user = new UserEntity("u-1", "Alice");
    user.setWalletBalance(3000);
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    int balance = walletService.cashOut("u-1", 750);

    assertEquals(3750, balance);
  }

  @Test
  void tryAutoRebuyShouldRefillStackWhenPlayerIsBusted() {
    UserEntity user = new UserEntity("u-1", "Alice");
    user.setWalletBalance(2000);
    when(userRepository.findByUserId("u-1")).thenReturn(Optional.of(user));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(roomPlayerRepository.findByRoomIdAndPlayerId("ROOM1", "p-1"))
        .thenReturn(Optional.of(new RoomPlayerEntity("ROOM1", "u-1", "p-1")));

    PlayerState player = new PlayerState("p-1", "Alice", 1, 0);
    boolean rebought = walletService.tryAutoRebuy("ROOM1", "p-1", player);

    assertTrue(rebought);
    assertEquals(1000, player.getStack());
    assertEquals(1000, user.getWalletBalance());
  }
}
