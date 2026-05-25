package com.pokerfriends.server.controller;

import com.pokerfriends.server.dto.ActionEventResponse;
import com.pokerfriends.server.dto.CreateRoomRequest;
import com.pokerfriends.server.dto.JoinRoomRequest;
import com.pokerfriends.server.dto.RoomOverviewResponse;
import com.pokerfriends.server.dto.RoomResponse;
import com.pokerfriends.server.service.ActionEventLogService;
import com.pokerfriends.server.service.RoomQueryService;
import com.pokerfriends.server.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
  private final RoomService roomService;
  private final RoomQueryService roomQueryService;
  private final ActionEventLogService actionEventLogService;

  public RoomController(
      RoomService roomService,
      RoomQueryService roomQueryService,
      ActionEventLogService actionEventLogService
  ) {
    this.roomService = roomService;
    this.roomQueryService = roomQueryService;
    this.actionEventLogService = actionEventLogService;
  }

  @PostMapping
  public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
    try {
      return roomService.createRoom(request);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/{roomId}/join")
  public RoomResponse joinRoom(@PathVariable String roomId, @Valid @RequestBody JoinRoomRequest request) {
    try {
      return roomService.joinRoom(roomId, request.playerName());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @GetMapping
  public List<RoomOverviewResponse> listRooms(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String status
  ) {
    try {
      return roomQueryService.listRecentRooms(limit, status);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 仅支持 OPEN/FULL/CLOSED");
    }
  }

  @GetMapping("/{roomId}")
  public RoomOverviewResponse getRoomOverview(@PathVariable String roomId) {
    return roomQueryService.getRoomOverview(roomId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在"));
  }

  @GetMapping("/{roomId}/actions")
  public List<ActionEventResponse> listRecentActions(
      @PathVariable String roomId,
      @RequestParam(defaultValue = "50") int limit
  ) {
    String tableId = roomQueryService.findTableIdByRoomId(roomId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在"));
    return actionEventLogService.listRecentByTableId(tableId, limit);
  }
}
