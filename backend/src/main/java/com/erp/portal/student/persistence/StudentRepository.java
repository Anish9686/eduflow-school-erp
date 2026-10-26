package com.erp.portal.student.persistence;

import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.api.StudentDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class StudentRepository {
    private final JdbcTemplate jdbc;

    public StudentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<StudentDtos.StudentSummary> list(String search, String status, String sortBy, String direction,
            int limit, int offset, SecurityPrincipal actor) {
        Query query = studentQuery(search, status, actor);
        String order = sortableStudentColumn(sortBy) + " " + direction;
        var params = new ArrayList<>(query.params());
        params.add(limit);
        params.add(offset);
        return jdbc.query("SELECT s.id,s.admission_no,s.first_name,s.last_name,s.status,s.admission_date,s.created_at " +
                        "FROM students s " + query.from() + " WHERE " + query.where() +
                        " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, row) -> new StudentDtos.StudentSummary(rs.getLong("id"), rs.getString("admission_no"),
                        rs.getString("first_name"), rs.getString("last_name"), rs.getString("status"),
                        localDate(rs.getDate("admission_date")), localDateTime(rs.getTimestamp("created_at"))),
                params.toArray());
    }

    public long count(String search, String status, SecurityPrincipal actor) {
        Query query = studentQuery(search, status, actor);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM students s " + query.from() + " WHERE " + query.where(),
                Long.class, query.params().toArray());
        return count == null ? 0 : count;
    }

    private Query studentQuery(String search, String status, SecurityPrincipal actor) {
        var predicates = new ArrayList<String>();
        var params = new ArrayList<Object>();
        if (search != null && !search.isBlank()) {
            predicates.add("(LOWER(s.admission_no) LIKE LOWER(?) OR LOWER(s.first_name) LIKE LOWER(?) OR " +
                    "LOWER(COALESCE(s.last_name,'')) LIKE LOWER(?) OR LOWER(COALESCE(s.email,'')) LIKE LOWER(?))");
            String term = "%" + search.trim() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }
        if (status != null) { predicates.add("s.status = ?"); params.add(status); }
        addScope(predicates, params, actor, "s");
        return new Query("", String.join(" AND ", predicates.isEmpty() ? List.of("1=1") : predicates), params);
    }

    private void addScope(List<String> predicates, List<Object> params, SecurityPrincipal actor, String studentAlias) {
        if (actor == null) return;
        var scopes = new ArrayList<String>();
        var scopeParams = new ArrayList<Object>();
        if (actor.roles().contains("STUDENT")) {
            scopes.add(studentAlias + ".user_id = ?");
            scopeParams.add(actor.userId());
        }
        if (actor.roles().contains("PARENT_GUARDIAN")) {
            scopes.add("EXISTS (SELECT 1 FROM student_guardians sg JOIN guardians g ON g.id=sg.guardian_id " +
                    "WHERE sg.student_id=" + studentAlias + ".id AND g.user_id=?)");
            scopeParams.add(actor.userId());
        }
        if (actor.roles().contains("TEACHER")) {
            scopes.add("EXISTS (SELECT 1 FROM student_enrollments se " +
                    "JOIN teacher_assignments ta ON ta.session_id=se.session_id AND ta.section_id=se.section_id " +
                    "JOIN staff st ON st.id=ta.staff_id WHERE se.student_id=" + studentAlias + ".id " +
                    "AND st.user_id=? AND ta.status='ACTIVE')");
            scopeParams.add(actor.userId());
        }
        if (actor.roles().contains("RECEPTION_ADMISSION")) {
            scopes.add("EXISTS (SELECT 1 FROM admissions admission_scope WHERE admission_scope.student_id=" + studentAlias + ".id)");
        }
        if (!scopes.isEmpty()) {
            predicates.add("(" + String.join(" OR ", scopes) + ")");
            params.addAll(scopeParams);
        }
    }

    private static String sortableStudentColumn(String key) {
        return switch (key) {
            case "admissionNo" -> "s.admission_no";
            case "firstName" -> "s.first_name";
            case "lastName" -> "s.last_name";
            case "status" -> "s.status";
            case "admissionDate" -> "s.admission_date";
            case "createdAt" -> "s.created_at";
            default -> "s.created_at";
        };
    }

    public long createStudent(StudentDtos.CreateStudentRequest request, String status, long actorId,
                              LocalDate admissionDate) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO students " +
                    "(admission_no,first_name,last_name,date_of_birth,gender,blood_group,religion,category,phone,email,address,photo_url,status,admission_date,created_by,updated_by) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.admissionNo().trim()); ps.setString(2, request.firstName().trim());
            ps.setString(3, trimToNull(request.lastName())); ps.setObject(4, request.dateOfBirth());
            ps.setString(5, request.gender()); ps.setString(6, request.bloodGroup()); ps.setString(7, request.religion());
            ps.setString(8, request.category()); ps.setString(9, request.phone()); ps.setString(10, request.email());
            ps.setString(11, request.address()); ps.setString(12, request.photoUrl()); ps.setString(13, status);
            ps.setObject(14, admissionDate); ps.setLong(15, actorId); ps.setLong(16, actorId);
            return ps;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Database did not return a student id");
        return id.longValue();
    }

    public void updateStudent(long id, StudentDtos.UpdateStudentRequest request, long actorId) {
        jdbc.update("UPDATE students SET first_name=?,last_name=?,date_of_birth=?,gender=?,blood_group=?,religion=?," +
                        "category=?,phone=?,email=?,address=?,photo_url=?,updated_by=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                request.firstName().trim(), trimToNull(request.lastName()), request.dateOfBirth(), request.gender(),
                request.bloodGroup(), request.religion(), request.category(), request.phone(), request.email(),
                request.address(), request.photoUrl(), actorId, id);
    }

    public Optional<StudentRecord> findStudent(long id, SecurityPrincipal actor) {
        var predicates = new ArrayList<String>(); var params = new ArrayList<Object>();
        predicates.add("s.id=?"); params.add(id); addScope(predicates, params, actor, "s");
        List<StudentRecord> rows = jdbc.query("SELECT s.* FROM students s WHERE " + String.join(" AND ", predicates),
                (rs, row) -> new StudentRecord(rs.getLong("id"), rs.getString("admission_no"), rs.getString("first_name"),
                        rs.getString("last_name"), localDate(rs.getDate("date_of_birth")), rs.getString("gender"),
                        rs.getString("blood_group"), rs.getString("religion"), rs.getString("category"),
                        rs.getString("phone"), rs.getString("email"), rs.getString("address"), rs.getString("photo_url"),
                        rs.getString("status"), localDate(rs.getDate("admission_date")), localDate(rs.getDate("exit_date")),
                        rs.getString("exit_reason"), localDateTime(rs.getTimestamp("created_at")),
                        localDateTime(rs.getTimestamp("updated_at"))), params.toArray());
        return rows.stream().findFirst();
    }

    public List<StudentDtos.GuardianResponse> guardians(long studentId) {
        return jdbc.query("SELECT g.id,g.name,g.phone,g.email,g.address,g.occupation,sg.relation,sg.is_primary " +
                        "FROM student_guardians sg JOIN guardians g ON g.id=sg.guardian_id WHERE sg.student_id=? " +
                        "ORDER BY sg.is_primary DESC,g.name",
                (rs, row) -> new StudentDtos.GuardianResponse(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("address"), rs.getString("occupation"),
                        rs.getString("relation"), rs.getBoolean("is_primary")), studentId);
    }

    public long addGuardian(long studentId, StudentDtos.GuardianRequest request) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO guardians(name,phone,email,address,occupation) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.name().trim()); ps.setString(2, request.phone()); ps.setString(3, request.email());
            ps.setString(4, request.address()); ps.setString(5, request.occupation()); return ps;
        }, key);
        Number guardianId = key.getKey();
        if (request.primary()) jdbc.update("UPDATE student_guardians SET is_primary=0 WHERE student_id=?", studentId);
        jdbc.update("INSERT INTO student_guardians(student_id,guardian_id,relation,is_primary) VALUES(?,?,?,?)",
                studentId, guardianId.longValue(), request.relation().trim(), request.primary());
        return guardianId.longValue();
    }

    public boolean guardianRelationshipExists(long studentId, long guardianId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM student_guardians WHERE student_id=? AND guardian_id=?", Integer.class, studentId, guardianId);
        return count != null && count > 0;
    }

    public void updateGuardianRelationship(long studentId, long guardianId, StudentDtos.GuardianRequest request) {
        if (request.primary()) jdbc.update("UPDATE student_guardians SET is_primary=0 WHERE student_id=?", studentId);
        jdbc.update("UPDATE guardians SET name=?,phone=?,email=?,address=?,occupation=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                request.name().trim(), request.phone(), request.email(), request.address(), request.occupation(), guardianId);
        jdbc.update("UPDATE student_guardians SET relation=?,is_primary=? WHERE student_id=? AND guardian_id=?",
                request.relation().trim(), request.primary(), studentId, guardianId);
    }

    public void unlinkGuardian(long studentId, long guardianId) {
        jdbc.update("DELETE FROM student_guardians WHERE student_id=? AND guardian_id=?", studentId, guardianId);
    }

    public long createAdmission(StudentDtos.CreateAdmissionRequest request, long actorId) {
        long studentId = createStudent(request.student(), "APPLIED", actorId, null);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO admissions(application_no,student_id,applicant_name,applied_on,status) VALUES(?,?,?,?, 'DRAFT')", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.applicationNo().trim()); ps.setLong(2, studentId);
            ps.setString(3, (request.student().firstName() + " " + (request.student().lastName() == null ? "" : request.student().lastName())).trim());
            ps.setObject(4, LocalDate.now()); return ps;
        }, key);
        Number admissionId = key.getKey();
        if (admissionId == null) throw new IllegalStateException("Database did not return an admission id");
        return admissionId.longValue();
    }

    public boolean admissionApplicationNumberExists(String applicationNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admissions WHERE application_no=?", Integer.class, applicationNo);
        return count != null && count > 0;
    }

    public boolean studentAdmissionNumberExists(String admissionNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM students WHERE admission_no=?", Integer.class, admissionNo);
        return count != null && count > 0;
    }

    public List<StudentDtos.AdmissionResponse> admissions(String status, String search, int limit, int offset,
                                                           SecurityPrincipal actor) {
        AdmissionQuery q = admissionQuery(status, search, actor);
        var args = new ArrayList<>(q.params()); args.add(limit); args.add(offset);
        return jdbc.query("SELECT a.id,a.application_no,a.student_id,a.applicant_name,a.applied_on,a.status,a.created_at " +
                        "FROM admissions a JOIN students s ON s.id=a.student_id WHERE " + q.where() +
                        " ORDER BY a.applied_on DESC,a.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new StudentDtos.AdmissionResponse(rs.getLong("id"),rs.getString("application_no"),
                        rs.getLong("student_id"),rs.getString("applicant_name"),localDate(rs.getDate("applied_on")),
                        rs.getString("status"),localDateTime(rs.getTimestamp("created_at"))), args.toArray());
    }

    public long admissionCount(String status, String search, SecurityPrincipal actor) {
        AdmissionQuery q = admissionQuery(status, search, actor);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM admissions a JOIN students s ON s.id=a.student_id WHERE " + q.where(),Long.class,q.params().toArray());
        return count == null ? 0 : count;
    }

    private AdmissionQuery admissionQuery(String status, String search, SecurityPrincipal actor) {
        var p=new ArrayList<String>(); var a=new ArrayList<Object>();
        if(status!=null){p.add("a.status=?");a.add(status);}
        if(search!=null&&!search.isBlank()){String term="%"+search.trim()+"%";p.add("(LOWER(a.application_no) LIKE LOWER(?) OR LOWER(a.applicant_name) LIKE LOWER(?) OR LOWER(s.admission_no) LIKE LOWER(?))");a.add(term);a.add(term);a.add(term);}
        addScope(p,a,actor,"s");
        return new AdmissionQuery(String.join(" AND ",p.isEmpty()?List.of("1=1"):p),a);
    }

    public Optional<StudentDtos.AdmissionResponse> findAdmission(long id, SecurityPrincipal actor) {
        AdmissionQuery q=admissionQuery(null,null,actor); var args=new ArrayList<>(q.params());args.add(id);
        List<StudentDtos.AdmissionResponse> rows=jdbc.query("SELECT a.id,a.application_no,a.student_id,a.applicant_name,a.applied_on,a.status,a.created_at " +
                        "FROM admissions a JOIN students s ON s.id=a.student_id WHERE "+q.where()+" AND a.id=?",
                (rs,row)->new StudentDtos.AdmissionResponse(rs.getLong("id"),rs.getString("application_no"),rs.getLong("student_id"),rs.getString("applicant_name"),localDate(rs.getDate("applied_on")),rs.getString("status"),localDateTime(rs.getTimestamp("created_at"))),args.toArray());
        return rows.stream().findFirst();
    }

    public boolean hasRequiredDocument(long studentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM student_documents WHERE student_id = ?", Integer.class, studentId);
        return count != null && count > 0;
    }

    public void changeAdmission(long id, String status, String note, long actorId) {
        jdbc.update("UPDATE admissions SET status=?,decision_at=CURRENT_TIMESTAMP(6),decision_by=?,decision_note=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",status,actorId,note,id);
    }

    public void activateStudent(long studentId) {
        jdbc.update("UPDATE students SET status='ACTIVE',admission_date=CURRENT_DATE,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",studentId);
    }

    public String admissionStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM admissions WHERE id=?",String.class,id);
    }

    public List<StudentDtos.StudentHistoryResponse> history(long studentId) {
        return jdbc.query("SELECT id,event_type,previous_value,new_value,note,changed_by,changed_at FROM student_history WHERE student_id=? ORDER BY changed_at DESC,id DESC",
                (rs,row)->new StudentDtos.StudentHistoryResponse(rs.getLong("id"),rs.getString("event_type"),rs.getString("previous_value"),rs.getString("new_value"),rs.getString("note"),(Long)rs.getObject("changed_by"),localDateTime(rs.getTimestamp("changed_at"))),studentId);
    }

    public void addHistory(long studentId, String type, String previous, String next, String note, Long actorId) {
        jdbc.update("INSERT INTO student_history(student_id,event_type,previous_value,new_value,note,changed_by) VALUES(?,?,?,?,?,?)",studentId,type,previous,next,note,actorId);
    }

    public void changeStudentStatus(long studentId, String previous, String next, LocalDate effectiveDate, String reason) {
        if ("EXITED".equals(next)) jdbc.update("UPDATE students SET status=?,exit_date=?,exit_reason=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",next,effectiveDate,reason,studentId);
        else jdbc.update("UPDATE students SET status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",next,studentId);
    }

    public long createDocument(long studentId, String type, String name, String path, long size, String mime, long actorId) {
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement("INSERT INTO student_documents(student_id,doc_type,file_name,file_path,file_size_bytes,mime_type,uploaded_by) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,studentId);ps.setString(2,type);ps.setString(3,name);ps.setString(4,path);ps.setLong(5,size);ps.setString(6,mime);ps.setLong(7,actorId);return ps;},key);
        return key.getKey().longValue();
    }

    public List<StudentDtos.StudentDocumentResponse> documents(long studentId) {
        return jdbc.query("SELECT id,doc_type,file_name,file_size_bytes,mime_type,uploaded_at,uploaded_by FROM student_documents WHERE student_id=? ORDER BY uploaded_at DESC,id DESC",
                (rs,row)->new StudentDtos.StudentDocumentResponse(rs.getLong("id"),rs.getString("doc_type"),rs.getString("file_name"),rs.getLong("file_size_bytes"),rs.getString("mime_type"),localDateTime(rs.getTimestamp("uploaded_at")),(Long)rs.getObject("uploaded_by")),studentId);
    }

    public Optional<DocumentRecord> findDocument(long studentId,long documentId) {
        List<DocumentRecord> rows=jdbc.query("SELECT id,doc_type,file_name,file_path,file_size_bytes,mime_type FROM student_documents WHERE student_id=? AND id=?",
                (rs,row)->new DocumentRecord(rs.getLong("id"),rs.getString("doc_type"),rs.getString("file_name"),rs.getString("file_path"),rs.getLong("file_size_bytes"),rs.getString("mime_type")),studentId,documentId);
        return rows.stream().findFirst();
    }

    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static LocalDate localDate(java.sql.Date value) { return value == null ? null : value.toLocalDate(); }
    private static LocalDateTime localDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private record Query(String from,String where,List<Object> params) { }
    private record AdmissionQuery(String where,List<Object> params) { }
    public record StudentRecord(long id,String admissionNo,String firstName,String lastName,LocalDate dateOfBirth,
            String gender,String bloodGroup,String religion,String category,String phone,String email,String address,
            String photoUrl,String status,LocalDate admissionDate,LocalDate exitDate,String exitReason,
            LocalDateTime createdAt,LocalDateTime updatedAt) { }
    public record DocumentRecord(long id,String docType,String fileName,String filePath,long fileSizeBytes,String mimeType) { }
}
