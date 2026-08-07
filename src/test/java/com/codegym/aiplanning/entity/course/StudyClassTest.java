package com.codegym.aiplanning.entity.course;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StudyClassTest {

    @Test
    void changeStatus_fromPlannedToActive_shouldSucceed() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.PLANNED);
        
        studyClass.changeStatus(ClassStatus.ACTIVE);
        
        assertEquals(ClassStatus.ACTIVE, studyClass.getStatus());
        assertNotNull(studyClass.getOpenedAt());
        assertNull(studyClass.getClosedAt());
    }

    @Test
    void changeStatus_fromActiveToClosed_shouldSucceed() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.ACTIVE);
        assertNotNull(studyClass.getOpenedAt()); // Because created as ACTIVE

        studyClass.changeStatus(ClassStatus.CLOSED);
        
        assertEquals(ClassStatus.CLOSED, studyClass.getStatus());
        assertNotNull(studyClass.getOpenedAt());
        assertNotNull(studyClass.getClosedAt());
    }

    @Test
    void changeStatus_fromPlannedToClosed_shouldThrowException() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.PLANNED);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            studyClass.changeStatus(ClassStatus.CLOSED)
        );
        assertTrue(exception.getMessage().contains("chỉ có thể chuyển sang trạng thái Đang hoạt động"));
    }

    @Test
    void changeStatus_fromActiveToPlanned_shouldThrowException() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.ACTIVE);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            studyClass.changeStatus(ClassStatus.PLANNED)
        );
        assertTrue(exception.getMessage().contains("chỉ có thể chuyển sang trạng thái Đã đóng"));
    }

    @Test
    void changeStatus_fromClosedToActive_shouldThrowException() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.CLOSED);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            studyClass.changeStatus(ClassStatus.ACTIVE)
        );
        assertTrue(exception.getMessage().contains("đã đóng không thể thay đổi trạng thái"));
    }

    @Test
    void changeStatus_toSameStatus_shouldThrowException() {
        StudyClass studyClass = StudyClass.create(UUID.randomUUID(), "C1", "Class 1", "Desc", ClassStatus.PLANNED);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            studyClass.changeStatus(ClassStatus.PLANNED)
        );
        assertTrue(exception.getMessage().contains("đã ở trạng thái"));
    }
}
