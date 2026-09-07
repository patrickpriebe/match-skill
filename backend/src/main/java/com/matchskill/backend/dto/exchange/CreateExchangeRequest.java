package com.matchskill.backend.dto.exchange;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateExchangeRequest(@NotNull UUID receiverId, @NotNull UUID skillFromReceiver) {}
