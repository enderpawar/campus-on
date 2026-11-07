package com.campuson.api.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.campuson.api.global.error.BusinessException;
import com.campuson.api.global.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventTest {

    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");
    private static final Instant DUE = Instant.parse("2026-11-08T05:00:00Z");

    @Test
    void importedEventCannotBeEditedOrDeleted() {
        Event imported = Event.imported(null, EventType.ASSIGNMENT, "과제 1", DUE, "eclass-1", NOW);

        BusinessException update = assertThrows(BusinessException.class,
                () -> imported.updatePersonal("바꾼 제목", null, null, DUE));
        BusinessException delete = assertThrows(BusinessException.class, imported::ensureDeletable);

        assertEquals(ErrorCode.EVENT_NOT_EDITABLE, update.code());
        assertEquals(ErrorCode.EVENT_NOT_EDITABLE, delete.code());
        assertEquals("과제 1", imported.getTitle());
    }

    @Test
    void startMustBeBeforeDue() {
        BusinessException same = assertThrows(BusinessException.class,
                () -> Event.personal(null, "회의", null, DUE, DUE, NOW));
        assertEquals(ErrorCode.INVALID_EVENT_TIME, same.code());

        Event event = assertDoesNotThrow(() -> Event.personal(null, "회의", null, DUE.minusSeconds(1), DUE, NOW));

        BusinessException update = assertThrows(BusinessException.class,
                () -> event.updatePersonal("회의", null, DUE.plusSeconds(1), DUE));
        assertEquals(ErrorCode.INVALID_EVENT_TIME, update.code());
    }
}
