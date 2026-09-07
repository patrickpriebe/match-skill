package com.matchskill.backend.dto.exchange;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** skillFromRequester is chosen by the receiver, from the requester's offered list. */
public record AcceptExchangeRequest(@NotNull UUID skillFromRequester) {}
