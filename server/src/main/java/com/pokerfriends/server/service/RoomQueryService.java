package com.pokerfriends.server.service;

import com.pokerfriends.server.dto.RoomOverviewResponse;
import com.pokerfriends.server.persistence.RoomEntity;
import com.pokerfriends.server.persistence.RoomRepository;
import com.pokerfriends.server.persistence.RoomStatus;
import com.pokerfriends.server.persistence.TableStateMetaEntity;
import com.pokerfriends.server.persistence.TableStateMetaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoomQueryService {
  private final RoomRepository roomRepository;
  private final TableStateMetaRepository tableStateMetaRepository;

  public RoomQueryService(RoomRepository roomRepository, TableStateMetaRepository tableStateMetaRepository) {
    this.roomRepository = roomRepository;
    this.tableStateMetaRepository = tableStateMetaRepository;
  }

  public Optional<RoomOverviewResponse> getRoomOverview(String roomId) {
    return roomRepository.findByRoomId(roomId)
        .map(room -> toRoomOverview(room, tableStateMetaRepository.findByTableId(room.getTableId()).orElse(null)));
  }

  public Optional<String> findTableIdByRoomId(String roomId) {
    return roomRepository.findByRoomId(roomId).map(RoomEntity::getTableId);
  }

  public List<RoomOverviewResponse> listRecentRooms(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    return roomRepository.findAll(
            PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        .stream()
        .map(room -> toRoomOverview(room, tableStateMetaRepository.findByTableId(room.getTableId()).orElse(null)))
        .toList();
  }

  public List<RoomOverviewResponse> listRecentRooms(int limit, RoomStatus status) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    return roomRepository.findByStatus(
            status,
            PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        .stream()
        .map(room -> toRoomOverview(room, tableStateMetaRepository.findByTableId(room.getTableId()).orElse(null)))
        .toList();
  }

  public List<RoomOverviewResponse> listRecentRooms(int limit, String status) {
    if (status == null || status.isBlank()) {
      return listRecentRooms(limit);
    }
    RoomStatus roomStatus = RoomStatus.valueOf(status.trim().toUpperCase());
    return listRecentRooms(limit, roomStatus);
  }

  private RoomOverviewResponse.TableMeta toTableMeta(TableStateMetaEntity tableStateMeta) {
    return new RoomOverviewResponse.TableMeta(
        tableStateMeta.getHandId(),
        tableStateMeta.getStage(),
        tableStateMeta.getPot(),
        tableStateMeta.getCurrentBet(),
        tableStateMeta.getActionPlayerId()
    );
  }

  private RoomOverviewResponse toRoomOverview(
      RoomEntity room,
      TableStateMetaEntity tableStateMeta
  ) {
    RoomOverviewResponse.TableMeta tableMeta = tableStateMeta == null ? null : toTableMeta(tableStateMeta);
    return new RoomOverviewResponse(
        room.getRoomId(),
        room.getTableId(),
        room.getSmallBlind(),
        room.getBigBlind(),
        room.getMaxPlayers(),
        room.getStatus().name(),
        room.getCreatedAt(),
        room.getUpdatedAt(),
        tableMeta
    );
  }
}
