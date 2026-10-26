package com.erp.portal.academic;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {
    @Mock AcademicRepository repo;
    @Mock AuditService audit;
    @Mock HttpServletRequest request;
    @InjectMocks AcademicService service;

    @Test
    void rejectsAcademicSessionWithInvalidDateRange(){
        var session=new AcademicDtos.SessionRequest("2026-27","2026–2027",LocalDate.parse("2027-03-31"),LocalDate.parse("2026-04-01"),false);
        assertThatThrownBy(()->service.createSession(session,admin(),request)).isInstanceOf(AcademicException.class).hasMessageContaining("after startDate");
        verifyNoInteractions(repo,audit);
    }

    @Test
    void mapsDuplicateSectionConstraintToConflict(){
        when(repo.exists("classes",2)).thenReturn(true);
        when(repo.createSection(any())).thenThrow(new DuplicateKeyException("uq_sections_class_name"));
        var section=new AcademicDtos.SectionRequest(2,"A",30,"101");
        assertThatThrownBy(()->service.createSection(section,admin(),request)).isInstanceOf(AcademicException.class).hasMessageContaining("already exists");
    }

    @Test
    void rejectsOverlappingTimetableBeforeInsert(){
        when(repo.exists("academic_sessions",1)).thenReturn(true);when(repo.exists("classes",2)).thenReturn(true);
        when(repo.exists("sections",3)).thenReturn(true);when(repo.exists("subjects",4)).thenReturn(true);
        when(repo.validClassSectionSubject(2,3,1,4)).thenReturn(true);when(repo.hasTimetableConflict(any(),isNull())).thenReturn(true);
        var entry=new AcademicDtos.TimetableRequest(1,2,3,4,null,1,LocalTime.of(9,0),LocalTime.of(9,45),"101");
        assertThatThrownBy(()->service.addTimetable(entry,admin(),request)).isInstanceOf(AcademicException.class).hasMessageContaining("overlapping");
        verify(repo).lockSession(1);verify(repo,never()).createTimetable(entry);
    }

    @Test
    void teacherCannotCreateHomeworkOutsideAssignedSectionAndSubject(){
        when(repo.exists("academic_sessions",1)).thenReturn(true);when(repo.exists("sections",3)).thenReturn(true);when(repo.exists("subjects",4)).thenReturn(true);
        when(repo.validSectionSubject(3,1,4)).thenReturn(true);when(repo.staffForUser(9)).thenReturn(15L);when(repo.isAssigned(15,1,3,4)).thenReturn(false);
        var homework=new AcademicDtos.HomeworkRequest(1,3,4,"Homework",null,LocalDate.parse("2026-10-10"));
        assertThatThrownBy(()->service.createHomework(homework,teacher(),request)).isInstanceOf(AcademicException.class).hasMessageContaining("not assigned");
        verify(repo,never()).createHomework(any(),anyLong());
    }

    @Test
    void teacherSyllabusProgressRequiresMatchingAssignment(){
        when(repo.exists("academic_sessions",1)).thenReturn(true);when(repo.exists("sections",3)).thenReturn(true);when(repo.exists("subjects",4)).thenReturn(true);
        when(repo.validSectionSubject(3,1,4)).thenReturn(true);when(repo.staffForUser(9)).thenReturn(15L);when(repo.isAssigned(15,1,3,4)).thenReturn(false);
        var progress=new AcademicDtos.SyllabusRequest(1,3,4,"Fractions","IN_PROGRESS");
        assertThatThrownBy(()->service.createSyllabus(progress,teacher(),request)).isInstanceOf(AcademicException.class);
        verify(repo,never()).createSyllabus(any(),anyLong());
    }

    private static SecurityPrincipal admin(){return new SecurityPrincipal(1,"admin","Admin",List.of("ADMIN"),Set.of("ACADEMIC:MANAGE","TIMETABLE:MANAGE"),"hash");}
    private static SecurityPrincipal teacher(){return new SecurityPrincipal(9,"teacher","Teacher",List.of("TEACHER"),Set.of("HOMEWORK:CREATE","SYLLABUS:UPDATE"),"hash");}
}
