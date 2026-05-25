package com.pokerfriends.server.service;

import com.pokerfriends.server.persistence.UserEntity;
import com.pokerfriends.server.persistence.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public UserEntity requireByDisplayName(String displayName) {
    return userRepository.findByDisplayName(displayName)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在，请先写入 users 表"));
  }
}
