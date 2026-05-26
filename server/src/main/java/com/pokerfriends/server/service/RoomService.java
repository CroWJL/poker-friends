package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.CreateRoomRequest;
import com.pokerfriends.server.dto.RoomResponse;
import com.pokerfriends.server.persistence.RoomEntity;
import com.pokerfriends.server.persistence.RoomPlayerEntity;
import com.pokerfriends.server.persistence.RoomPlayerRepository;
import com.pokerfriends.server.persistence.RoomRepository;
import com.pokerfriends.server.persistence.RoomStatus;
import com.pokerfriends.server.persistence.UserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RoomService {
  private final SecureRandom secureRandom = new SecureRandom();
  private final AtomicInteger playerCounter = new AtomicInteger(1);
  private final RoomRepository roomRepository;
  private final AuthTokenService authTokenService;
  private final TableEngineService tableEngineService;
  private final TableStateMetaService tableStateMetaService;
  private final TableStateSnapshotService tableStateSnapshotService;
  private final UserService userService;
  private final RoomPlayerRepository roomPlayerRepository;

  public RoomService(
      RoomRepository roomRepository,
      AuthTokenService authTokenService,
      TableEngineService tableEngineService,
      TableStateMetaService tableStateMetaService,
      TableStateSnapshotService tableStateSnapshotService,
      UserService userService,
      RoomPlayerRepository roomPlayerRepository
  ) {
    this.roomRepository = roomRepository;
    this.authTokenService = authTokenService;
    this.tableEngineService = tableEngineService;
    this.tableStateMetaService = tableStateMetaService;
    this.tableStateSnapshotService = tableStateSnapshotService;
    this.userService = userService;
    this.roomPlayerRepository = roomPlayerRepository;
  }

  public RoomResponse createRoom(CreateRoomRequest request) {
    UserEntity user = userService.requireByDisplayName(request.hostName());
    for (int i = 0; i < 10; i++) {
      String roomId = randomRoomId();
      String tableId = "table-" + roomId;
      try {
        RoomEntity room = roomRepository.save(new RoomEntity(
            roomId,
            tableId,
            request.smallBlind(),
            request.bigBlind(),
            request.maxPlayers(),
            RoomStatus.OPEN
        ));
        String playerId = nextPlayerId();
        tableEngineService.initTable(tableId, request.smallBlind(), request.bigBlind(), request.maxPlayers());
        tableEngineService.addPlayer(tableId, playerId, user.getDisplayName());
        var snapshot = tableEngineService.getSnapshot(tableId);
        tableStateMetaService.upsert(snapshot);
        tableStateSnapshotService.upsert(snapshot);
        roomPlayerRepository.save(new RoomPlayerEntity(roomId, user.getUserId(), playerId));
        refreshRoomStatus(room);
        return new RoomResponse(roomId, tableId, playerId, authTokenService.issueToken(tableId, playerId));
      } catch (DataIntegrityViolationException duplicate) {
        if (isRoomIdentityConflict(duplicate)) {
          // roomId/tableId 唯一索引冲突，重试生成新的房间号。
          continue;
        }
        throw duplicate;
      }
    }
    throw new IllegalStateException("无法生成唯一房间号");
  }

  public RoomResponse joinRoom(String roomId, String playerName) {
    UserEntity user = userService.requireByDisplayName(playerName);
    String userId = user.getUserId();
    RoomEntity room = roomRepository.findByRoomId(roomId).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
    RoomPlayerEntity existed = roomPlayerRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
    if (existed != null) {
      return new RoomResponse(roomId, room.getTableId(), existed.getPlayerId(), authTokenService.issueToken(room.getTableId(), existed.getPlayerId()));
    }
    ensureTableLoaded(room);
    String tableId = room.getTableId();
    if (tableEngineService.playerCount(tableId) >= room.getMaxPlayers()) {
      throw new IllegalStateException("房间已满");
    }
    String playerId = nextPlayerId();
    tableEngineService.addPlayer(tableId, playerId, user.getDisplayName());
    var snapshot = tableEngineService.getSnapshot(tableId);
    tableStateMetaService.upsert(snapshot);
    tableStateSnapshotService.upsert(snapshot);
    roomPlayerRepository.save(new RoomPlayerEntity(roomId, userId, playerId));
    refreshRoomStatus(room);
    return new RoomResponse(roomId, tableId, playerId, authTokenService.issueToken(tableId, playerId));
  }

  public void ensureTableLoadedByTableId(String tableId) {
    RoomEntity room = roomRepository.findByTableId(tableId)
        .orElseThrow(() -> new IllegalArgumentException("牌桌不存在"));
    ensureTableLoaded(room);
  }

  private void ensureTableLoaded(RoomEntity room) {
    try {
      tableEngineService.playerCount(room.getTableId());
    } catch (IllegalArgumentException ignored) {
      tableStateSnapshotService.load(room.getTableId()).ifPresentOrElse(
          snapshot -> tableEngineService.restoreTable(
              room.getTableId(),
              room.getSmallBlind(),
              room.getBigBlind(),
              room.getMaxPlayers(),
              snapshot
          ),
          () -> tableEngineService.initTable(room.getTableId(), room.getSmallBlind(), room.getBigBlind(), room.getMaxPlayers())
      );
      var snapshot = tableEngineService.getSnapshot(room.getTableId());
      tableStateMetaService.upsert(snapshot);
      tableStateSnapshotService.upsert(snapshot);
    }
  }

  private void refreshRoomStatus(RoomEntity room) {
    int playerCount = tableEngineService.playerCount(room.getTableId());
    RoomStatus targetStatus = playerCount >= room.getMaxPlayers() ? RoomStatus.FULL : RoomStatus.OPEN;
    if (room.getStatus() != targetStatus) {
      room.setStatus(targetStatus);
      roomRepository.save(room);
    }
  }

  private String randomRoomId() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      builder.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
    }
    return builder.toString();
  }

  private String nextPlayerId() {
    return "p-" + playerCounter.getAndIncrement();
  }

  private boolean isRoomIdentityConflict(DataIntegrityViolationException exception) {
    Throwable root = exception;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    String message = root.getMessage();
    if (message == null) {
      return false;
    }
    return message.contains("rooms_room_id_key") || message.contains("rooms_table_id_key");
  }
}
