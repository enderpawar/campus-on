package com.campuson.api.schedule;

import jakarta.validation.constraints.NotNull;

public record UpdateEventDoneRequest(@NotNull Boolean done) {
}
