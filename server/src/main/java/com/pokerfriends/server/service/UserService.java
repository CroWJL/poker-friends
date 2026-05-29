package com.pokerfriends.server.service;

import com.pokerfriends.server.persistence.UserEntity;
import com.pokerfriends.server.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

  public UserEntity findOrCreateByDisplayName(String displayName) {
    String normalized = displayName.trim();
    return userRepository.findByDisplayName(normalized).orElseGet(() -> {
      String userId = "u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
      try {
        return userRepository.save(new UserEntity(userId, normalized));
      } catch (DataIntegrityViolationException duplicate) {
        return userRepository.findByDisplayName(normalized)
            .orElseThrow(() -> new IllegalStateException("创建用户失败"));
      }
    });
  }
}
