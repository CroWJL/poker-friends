package com.pokerfriends.server.model;

import java.util.List;

public record SidePot(int amount, List<String> eligiblePlayerIds) {
}
