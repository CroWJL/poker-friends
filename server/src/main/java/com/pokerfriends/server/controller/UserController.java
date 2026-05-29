package com.pokerfriends.server.controller;

import com.pokerfriends.server.dto.UserProfileResponse;
import com.pokerfriends.server.dto.WalletResponse;
import com.pokerfriends.server.dto.WalletTransactionResponse;
import com.pokerfriends.server.persistence.UserEntity;
import com.pokerfriends.server.service.UserService;
import com.pokerfriends.server.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;
  private final WalletService walletService;

  public UserController(UserService userService, WalletService walletService) {
    this.userService = userService;
    this.walletService = walletService;
  }

  @GetMapping("/profile")
  public UserProfileResponse getProfile(@RequestParam String displayName) {
    try {
      UserEntity user = userService.requireByDisplayName(displayName);
      return new UserProfileResponse(
          user.getUserId(),
          user.getDisplayName(),
          walletService.getBalance(user.getUserId())
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/wallet/transactions")
  public List<WalletTransactionResponse> listWalletTransactions(
      @RequestParam String displayName,
      @RequestParam(defaultValue = "30") int limit
  ) {
    try {
      UserEntity user = userService.requireByDisplayName(displayName);
      return walletService.listRecentTransactions(user.getUserId(), limit);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/wallet")
  public WalletResponse getWallet(@RequestParam String displayName) {
    try {
      UserProfileResponse profile = getProfile(displayName);
      return new WalletResponse(profile.displayName(), profile.walletBalance());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }
}
