package com.erp.portal.student.persistence;

import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.api.StudentDtos;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRepositoryScopeTest {
    private JdbcTemplate jdbc;
    private StudentRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:student-scope-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE students (id BIGINT PRIMARY KEY, admission_no VARCHAR(30), first_name VARCHAR(100), last_name VARCHAR(100), status VARCHAR(20), admission_date DATE, created_at TIMESTAMP, user_id BIGINT, date_of_birth DATE, gender VARCHAR(20), blood_group VARCHAR(20), religion VARCHAR(50), category VARCHAR(50), phone VARCHAR(30), email VARCHAR(100), address VARCHAR(200), photo_url VARCHAR(200), exit_date DATE, exit_reason VARCHAR(200), updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE staff (id BIGINT PRIMARY KEY, user_id BIGINT)");
        jdbc.execute("CREATE TABLE student_enrollments (student_id BIGINT, session_id BIGINT, section_id BIGINT)");
        jdbc.execute("CREATE TABLE teacher_assignments (staff_id BIGINT, session_id BIGINT, section_id BIGINT, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE admissions (id BIGINT PRIMARY KEY, student_id BIGINT)");
        jdbc.execute("CREATE TABLE guardians (id BIGINT PRIMARY KEY, user_id BIGINT)");
        jdbc.execute("CREATE TABLE student_guardians (student_id BIGINT, guardian_id BIGINT)");
        jdbc.update("INSERT INTO students(id,admission_no,first_name,status,created_at) VALUES (1,'S-1','Assigned','ACTIVE',CURRENT_TIMESTAMP),(2,'S-2','Other','ACTIVE',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO staff(id,user_id) VALUES(10,100)");
        jdbc.update("INSERT INTO student_enrollments(student_id,session_id,section_id) VALUES(1,20,30),(2,20,31)");
        jdbc.update("INSERT INTO teacher_assignments(staff_id,session_id,section_id,status) VALUES(10,20,30,'ACTIVE')");
        repository = new StudentRepository(jdbc);
    }

    @Test
    void teacherListAndDetailAreLimitedToActiveAssignedSections() {
        SecurityPrincipal teacher = new SecurityPrincipal(100, "teacher", "Teacher", List.of("TEACHER"), Set.of("STUDENT:READ"), "hash");

        assertThat(repository.list(null, null, "createdAt", "ASC", 25, 0, teacher))
                .extracting(StudentDtos.StudentSummary::id).containsExactly(1L);
        assertThat(repository.findStudent(1, teacher)).isPresent();
        assertThat(repository.findStudent(2, teacher)).isEmpty();
    }

    @Test
    void receptionScopeOnlyReturnsApplicantStudents() {
        jdbc.update("INSERT INTO admissions(id,student_id) VALUES(1,2)");
        SecurityPrincipal reception = new SecurityPrincipal(200, "admissions", "Admissions", List.of("RECEPTION_ADMISSION"), Set.of("ADMISSION:READ"), "hash");

        assertThat(repository.list(null, null, "createdAt", "ASC", 25, 0, reception))
                .extracting(StudentDtos.StudentSummary::id).containsExactly(2L);
        assertThat(repository.findStudent(1, reception)).isEmpty();
        assertThat(repository.findStudent(2, reception)).isPresent();
    }

    @Test
    void parentGuardianScopeOnlyReturnsLinkedChildren() {
        jdbc.update("INSERT INTO guardians(id,user_id) VALUES (50, 300)");
        jdbc.update("INSERT INTO student_guardians(student_id,guardian_id) VALUES (1, 50)");
        SecurityPrincipal parent = new SecurityPrincipal(300, "parent", "Parent", List.of("PARENT_GUARDIAN"), Set.of("STUDENT:READ"), "hash");

        assertThat(repository.list(null, null, "createdAt", "ASC", 25, 0, parent))
                .extracting(StudentDtos.StudentSummary::id).containsExactly(1L);
        assertThat(repository.findStudent(1, parent)).isPresent();
        assertThat(repository.findStudent(2, parent)).isEmpty();

        SecurityPrincipal unlinkedParent = new SecurityPrincipal(301, "unlinked", "Unlinked Parent", List.of("PARENT_GUARDIAN"), Set.of("STUDENT:READ"), "hash");
        assertThat(repository.list(null, null, "createdAt", "ASC", 25, 0, unlinkedParent)).isEmpty();
        assertThat(repository.findStudent(1, unlinkedParent)).isEmpty();
    }
}
