package com.erp.portal.academic;

import com.erp.portal.academic.AcademicDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AcademicRepository {
    private final JdbcTemplate jdbc;
    public AcademicRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SessionResponse> sessions() {
        return jdbc.query("SELECT * FROM academic_sessions ORDER BY start_date DESC,id DESC", (r,n) ->
                new SessionResponse(r.getLong("id"),r.getString("code"),r.getString("name"),date(r,"start_date"),date(r,"end_date"),r.getBoolean("is_active"),dateTime(r,"created_at")));
    }
    public Optional<SessionResponse> session(long id) { return sessions().stream().filter(x -> x.id()==id).findFirst(); }
    public long createSession(SessionRequest x) {
        if (x.active()) jdbc.update("UPDATE academic_sessions SET is_active=0 WHERE is_active=1");
        return insert("INSERT INTO academic_sessions(code,name,start_date,end_date,is_active) VALUES(?,?,?,?,?)",x.code().trim(),x.name().trim(),x.startDate(),x.endDate(),x.active());
    }
    public int updateSession(long id, SessionRequest x) {
        if (x.active()) jdbc.update("UPDATE academic_sessions SET is_active=0 WHERE is_active=1 AND id<>?",id);
        return jdbc.update("UPDATE academic_sessions SET code=?,name=?,start_date=?,end_date=?,is_active=? WHERE id=?",x.code().trim(),x.name().trim(),x.startDate(),x.endDate(),x.active(),id);
    }
    public int setSessionActive(long id, boolean active) {
        if (active) jdbc.update("UPDATE academic_sessions SET is_active=0 WHERE is_active=1 AND id<>?",id);
        return jdbc.update("UPDATE academic_sessions SET is_active=? WHERE id=?",active,id);
    }

    public List<ClassResponse> classes() { return jdbc.query("SELECT id,name,order_seq,created_at FROM classes ORDER BY order_seq,name",(r,n)->new ClassResponse(r.getLong("id"),r.getString("name"),r.getInt("order_seq"),dateTime(r,"created_at"))); }
    public long createClass(ClassRequest x) { return insert("INSERT INTO classes(name,order_seq) VALUES(?,?)",x.name().trim(),x.orderSeq()); }
    public int updateClass(long id, ClassRequest x) { return jdbc.update("UPDATE classes SET name=?,order_seq=? WHERE id=?",x.name().trim(),x.orderSeq(),id); }

    public List<SectionResponse> sections(Long classId) { return sections(classId, null); }
    public List<SectionResponse> sections(Long classId, List<Long> allowedSectionIds) {
        StringBuilder sql = new StringBuilder("SELECT s.id,s.class_id,c.name class_name,s.name,s.capacity,s.room_no,s.created_at FROM sections s JOIN classes c ON c.id=s.class_id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (classId != null) { sql.append(" AND s.class_id=?"); args.add(classId); }
        if (allowedSectionIds != null) {
            if (allowedSectionIds.isEmpty()) return List.of();
            sql.append(" AND s.id IN (").append(String.join(",", allowedSectionIds.stream().map(String::valueOf).toList())).append(")");
        }
        sql.append(" ORDER BY c.order_seq,c.name,s.name");
        return jdbc.query(sql.toString(),
                (r,n)->new SectionResponse(r.getLong("id"),r.getLong("class_id"),r.getString("class_name"),r.getString("name"),nullableInt(r,"capacity"),r.getString("room_no"),dateTime(r,"created_at")),args.toArray());
    }
    public long createSection(SectionRequest x) { return insert("INSERT INTO sections(class_id,name,capacity,room_no) VALUES(?,?,?,?)",x.classId(),x.name().trim(),x.capacity(),x.roomNo()); }
    public int updateSection(long id, SectionRequest x) { return jdbc.update("UPDATE sections SET class_id=?,name=?,capacity=?,room_no=? WHERE id=?",x.classId(),x.name().trim(),x.capacity(),x.roomNo(),id); }

    public List<SubjectResponse> subjects() { return jdbc.query("SELECT id,code,name,description,created_at FROM subjects ORDER BY name,code",(r,n)->new SubjectResponse(r.getLong("id"),r.getString("code"),r.getString("name"),r.getString("description"),dateTime(r,"created_at"))); }
    public long createSubject(SubjectRequest x) { return insert("INSERT INTO subjects(code,name,description) VALUES(?,?,?)",x.code().trim(),x.name().trim(),x.description()); }
    public int updateSubject(long id, SubjectRequest x) { return jdbc.update("UPDATE subjects SET code=?,name=?,description=? WHERE id=?",x.code().trim(),x.name().trim(),x.description(),id); }

    public List<ClassSubjectResponse> classSubjects(Long sessionId, Long classId) {
        return jdbc.query("SELECT cs.id,cs.session_id,cs.class_id,c.name class_name,cs.subject_id,s.code subject_code,s.name subject_name,cs.is_active,cs.created_at FROM class_subjects cs JOIN classes c ON c.id=cs.class_id JOIN subjects s ON s.id=cs.subject_id WHERE (? IS NULL OR cs.session_id=?) AND (? IS NULL OR cs.class_id=?) ORDER BY c.order_seq,c.name,s.name",
                (r,n)->new ClassSubjectResponse(r.getLong("id"),r.getLong("session_id"),r.getLong("class_id"),r.getString("class_name"),r.getLong("subject_id"),r.getString("subject_code"),r.getString("subject_name"),r.getBoolean("is_active"),dateTime(r,"created_at")),sessionId,sessionId,classId,classId);
    }
    public long createClassSubject(ClassSubjectRequest x) { return insert("INSERT INTO class_subjects(session_id,class_id,subject_id,is_active) VALUES(?,?,?,?)",x.sessionId(),x.classId(),x.subjectId(),x.active()); }
    public int deactivateClassSubject(long id) { return jdbc.update("UPDATE class_subjects SET is_active=0 WHERE id=?",id); }
    public boolean classSubjectInUse(long id) {
        Integer count=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM teacher_assignments ta JOIN sections sec ON sec.id=ta.section_id JOIN class_subjects cs ON cs.session_id=ta.session_id AND cs.class_id=sec.class_id AND cs.subject_id=ta.subject_id WHERE cs.id=? AND ta.status='ACTIVE') + (SELECT COUNT(*) FROM timetable t JOIN class_subjects cs ON cs.session_id=t.session_id AND cs.class_id=t.class_id AND cs.subject_id=t.subject_id WHERE cs.id=?)",Integer.class,id,id);
        return count!=null&&count>0;
    }
    public boolean sectionHasAcademicRecords(long id) {
        Integer count=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM teacher_assignments WHERE section_id=?) + (SELECT COUNT(*) FROM timetable WHERE section_id=?) + (SELECT COUNT(*) FROM homework WHERE section_id=?) + (SELECT COUNT(*) FROM syllabus_progress WHERE section_id=?)",Integer.class,id,id,id,id);
        return count!=null&&count>0;
    }
    public long sectionClassId(long id) { return jdbc.queryForObject("SELECT class_id FROM sections WHERE id=?",Long.class,id); }

    public List<TeacherAssignmentResponse> teacherAssignments(Long sessionId, Long staffId) {
        return jdbc.query("SELECT ta.id,ta.session_id,c.id class_id,c.name class_name,sec.id section_id,sec.name section_name,s.id subject_id,s.name subject_name,ta.staff_id,CONCAT(st.first_name,' ',COALESCE(st.last_name,'')) teacher_name,ta.status,ta.assigned_at FROM teacher_assignments ta JOIN sections sec ON sec.id=ta.section_id JOIN classes c ON c.id=sec.class_id JOIN subjects s ON s.id=ta.subject_id JOIN staff st ON st.id=ta.staff_id WHERE (? IS NULL OR ta.session_id=?) AND (? IS NULL OR ta.staff_id=?) ORDER BY c.order_seq,sec.name,s.name,teacher_name",
                (r,n)->new TeacherAssignmentResponse(r.getLong("id"),r.getLong("session_id"),r.getLong("class_id"),r.getString("class_name"),r.getLong("section_id"),r.getString("section_name"),r.getLong("subject_id"),r.getString("subject_name"),r.getLong("staff_id"),r.getString("teacher_name").trim(),r.getString("status"),dateTime(r,"assigned_at")),sessionId,sessionId,staffId,staffId);
    }
    public List<TeacherOption> teachers() { return jdbc.query("SELECT s.id,s.employee_no,CONCAT(s.first_name,' ',COALESCE(s.last_name,'')) name,s.email FROM staff s JOIN user_roles ur ON ur.user_id=s.user_id JOIN roles r ON r.id=ur.role_id WHERE s.status='ACTIVE' AND r.code='TEACHER' ORDER BY s.first_name,s.last_name",(r,n)->new TeacherOption(r.getLong("id"),r.getString("employee_no"),r.getString("name").trim(),r.getString("email"))); }
    public long createTeacherAssignment(TeacherAssignmentRequest x) {
        List<Long> existing=jdbc.query("SELECT id FROM teacher_assignments WHERE session_id=? AND section_id=? AND subject_id=? AND staff_id=?",(r,n)->r.getLong(1),x.sessionId(),x.sectionId(),x.subjectId(),x.staffId());
        if(!existing.isEmpty()){jdbc.update("UPDATE teacher_assignments SET status='ACTIVE' WHERE id=?",existing.getFirst());return existing.getFirst();}
        return insert("INSERT INTO teacher_assignments(session_id,section_id,subject_id,staff_id,status) VALUES(?,?,?,?,'ACTIVE')",x.sessionId(),x.sectionId(),x.subjectId(),x.staffId());
    }
    public int deactivateTeacherAssignment(long id) { return jdbc.update("UPDATE teacher_assignments SET status='INACTIVE' WHERE id=?",id); }

    public List<TimetableResponse> timetable(Long sessionId, Long sectionId, Long teacherId) {
        return timetable(sessionId, sectionId, teacherId, null);
    }
    public List<TimetableResponse> timetable(Long sessionId, Long sectionId, Long teacherId, List<Long> allowedSections) {
        StringBuilder sql = new StringBuilder("SELECT t.id,t.session_id,t.class_id,c.name class_name,t.section_id,sec.name section_name,t.subject_id,s.name subject_name,t.teacher_id,CONCAT(st.first_name,' ',COALESCE(st.last_name,'')) teacher_name,t.day_of_week,t.start_time,t.end_time,t.room_no FROM timetable t JOIN classes c ON c.id=t.class_id JOIN sections sec ON sec.id=t.section_id JOIN subjects s ON s.id=t.subject_id LEFT JOIN staff st ON st.id=t.teacher_id WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (sessionId != null) { sql.append(" AND t.session_id=?"); args.add(sessionId); }
        if (sectionId != null) { sql.append(" AND t.section_id=?"); args.add(sectionId); }
        if (teacherId != null) { sql.append(" AND t.teacher_id=?"); args.add(teacherId); }
        if (allowedSections != null) {
            if (allowedSections.isEmpty()) return List.of();
            sql.append(" AND t.section_id IN (").append(String.join(",", allowedSections.stream().map(String::valueOf).toList())).append(")");
        }
        sql.append(" ORDER BY t.day_of_week,t.start_time,c.order_seq,sec.name");
        return jdbc.query(sql.toString(),
                (r,n)->new TimetableResponse(r.getLong("id"),r.getLong("session_id"),r.getLong("class_id"),r.getString("class_name"),r.getLong("section_id"),r.getString("section_name"),r.getLong("subject_id"),r.getString("subject_name"),nullableLong(r,"teacher_id"),r.getString("teacher_name")==null?null:r.getString("teacher_name").trim(),r.getInt("day_of_week"),time(r,"start_time"),time(r,"end_time"),r.getString("room_no")),args.toArray());
    }
    public void lockSession(long id) { jdbc.queryForObject("SELECT id FROM academic_sessions WHERE id=? FOR UPDATE",Long.class,id); }
    public boolean hasTimetableConflict(TimetableRequest x, Long excludeId) {
        Long collisions=jdbc.queryForObject("SELECT COUNT(*) FROM timetable t WHERE t.session_id=? AND t.day_of_week=? AND (? IS NULL OR t.id<>?) AND t.start_time < ? AND t.end_time > ? AND (t.section_id=? OR (? IS NOT NULL AND t.teacher_id=?))",
                Long.class,x.sessionId(),x.dayOfWeek(),excludeId,excludeId,x.endTime(),x.startTime(),x.sectionId(),x.teacherId(),x.teacherId());
        return collisions!=null && collisions>0;
    }
    public long createTimetable(TimetableRequest x) { return insert("INSERT INTO timetable(session_id,class_id,section_id,subject_id,teacher_id,day_of_week,start_time,end_time,room_no) VALUES(?,?,?,?,?,?,?,?,?)",x.sessionId(),x.classId(),x.sectionId(),x.subjectId(),x.teacherId(),x.dayOfWeek(),x.startTime(),x.endTime(),x.roomNo()); }
    public int updateTimetable(long id, TimetableRequest x) { return jdbc.update("UPDATE timetable SET session_id=?,class_id=?,section_id=?,subject_id=?,teacher_id=?,day_of_week=?,start_time=?,end_time=?,room_no=? WHERE id=?",x.sessionId(),x.classId(),x.sectionId(),x.subjectId(),x.teacherId(),x.dayOfWeek(),x.startTime(),x.endTime(),x.roomNo(),id); }
    public int deleteTimetable(long id) { return jdbc.update("DELETE FROM timetable WHERE id=?",id); }

    public List<HomeworkResponse> homework(Long sessionId, Long sectionId, Long assignedBy) {
        return homework(sessionId, sectionId, assignedBy, null);
    }
    public List<HomeworkResponse> homework(Long sessionId, Long sectionId, Long assignedBy, List<Long> allowedSections) {
        StringBuilder sql = new StringBuilder("SELECT h.id,h.session_id,c.id class_id,c.name class_name,h.section_id,sec.name section_name,h.subject_id,s.name subject_name,h.assigned_by,CONCAT(st.first_name,' ',COALESCE(st.last_name,'')) teacher_name,h.title,h.description,h.due_date,h.created_at FROM homework h JOIN sections sec ON sec.id=h.section_id JOIN classes c ON c.id=sec.class_id JOIN subjects s ON s.id=h.subject_id JOIN staff st ON st.id=h.assigned_by WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (sessionId != null) { sql.append(" AND h.session_id=?"); args.add(sessionId); }
        if (sectionId != null) { sql.append(" AND h.section_id=?"); args.add(sectionId); }
        if (assignedBy != null) { sql.append(" AND h.assigned_by=?"); args.add(assignedBy); }
        if (allowedSections != null) {
            if (allowedSections.isEmpty()) return List.of();
            sql.append(" AND h.section_id IN (").append(String.join(",", allowedSections.stream().map(String::valueOf).toList())).append(")");
        }
        sql.append(" ORDER BY h.due_date DESC,h.id DESC");
        return jdbc.query(sql.toString(),
                (r,n)->new HomeworkResponse(r.getLong("id"),r.getLong("session_id"),r.getLong("class_id"),r.getString("class_name"),r.getLong("section_id"),r.getString("section_name"),r.getLong("subject_id"),r.getString("subject_name"),r.getLong("assigned_by"),r.getString("teacher_name").trim(),r.getString("title"),r.getString("description"),date(r,"due_date"),dateTime(r,"created_at")),args.toArray());
    }
    public long createHomework(HomeworkRequest x, long staffId) { return insert("INSERT INTO homework(session_id,section_id,subject_id,assigned_by,title,description,due_date) VALUES(?,?,?,?,?,?,?)",x.sessionId(),x.sectionId(),x.subjectId(),staffId,x.title().trim(),x.description(),x.dueDate()); }
    public int deleteHomework(long id, Long assignedBy) { return jdbc.update("DELETE FROM homework WHERE id=? AND (? IS NULL OR assigned_by=?)",id,assignedBy,assignedBy); }

    public List<SyllabusResponse> syllabus(Long sessionId, Long sectionId, Long subjectId, Long recordedBy) {
        return syllabus(sessionId, sectionId, subjectId, recordedBy, null);
    }
    public List<SyllabusResponse> syllabus(Long sessionId, Long sectionId, Long subjectId, Long recordedBy, List<Long> allowedSections) {
        StringBuilder sql = new StringBuilder("SELECT sp.id,sp.session_id,c.id class_id,c.name class_name,sp.section_id,sec.name section_name,sp.subject_id,s.name subject_name,sp.topic,sp.status,sp.completed_at,sp.recorded_by,CONCAT(st.first_name,' ',COALESCE(st.last_name,'')) actor_name,sp.created_at,sp.updated_at FROM syllabus_progress sp JOIN sections sec ON sec.id=sp.section_id JOIN classes c ON c.id=sec.class_id JOIN subjects s ON s.id=sp.subject_id JOIN staff st ON st.id=sp.recorded_by WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        if (sessionId != null) { sql.append(" AND sp.session_id=?"); args.add(sessionId); }
        if (sectionId != null) { sql.append(" AND sp.section_id=?"); args.add(sectionId); }
        if (subjectId != null) { sql.append(" AND sp.subject_id=?"); args.add(subjectId); }
        if (recordedBy != null) { sql.append(" AND sp.recorded_by=?"); args.add(recordedBy); }
        if (allowedSections != null) {
            if (allowedSections.isEmpty()) return List.of();
            sql.append(" AND sp.section_id IN (").append(String.join(",", allowedSections.stream().map(String::valueOf).toList())).append(")");
        }
        sql.append(" ORDER BY c.order_seq,sec.name,s.name,sp.topic");
        return jdbc.query(sql.toString(),
                (r,n)->new SyllabusResponse(r.getLong("id"),r.getLong("session_id"),r.getLong("class_id"),r.getString("class_name"),r.getLong("section_id"),r.getString("section_name"),r.getLong("subject_id"),r.getString("subject_name"),r.getString("topic"),r.getString("status"),dateTime(r,"completed_at"),r.getLong("recorded_by"),r.getString("actor_name").trim(),dateTime(r,"created_at"),dateTime(r,"updated_at")),args.toArray());
    }
    public long createSyllabus(SyllabusRequest x, long staffId) { return insert("INSERT INTO syllabus_progress(session_id,section_id,subject_id,topic,status,completed_at,recorded_by) VALUES(?,?,?,?,?,?,?)",x.sessionId(),x.sectionId(),x.subjectId(),x.topic().trim(),x.status(),"COMPLETED".equals(x.status())?LocalDateTime.now():null,staffId); }
    public int updateSyllabus(long id, SyllabusRequest x) { return jdbc.update("UPDATE syllabus_progress SET session_id=?,section_id=?,subject_id=?,topic=?,status=?,completed_at=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",x.sessionId(),x.sectionId(),x.subjectId(),x.topic().trim(),x.status(),"COMPLETED".equals(x.status())?LocalDateTime.now():null,id); }
    public Optional<SyllabusResponse> syllabusItem(long id) { return syllabus(null,null,null,null).stream().filter(x->x.id()==id).findFirst(); }

    public List<Long> studentSectionsForUser(long userId) {
        return jdbc.query("SELECT DISTINCT se.section_id FROM student_enrollments se " +
                "JOIN students s ON s.id = se.student_id WHERE s.user_id = ?",
                (r, n) -> r.getLong(1), userId);
    }

    public List<Long> parentSectionsForUser(long userId) {
        return jdbc.query("SELECT DISTINCT se.section_id FROM student_enrollments se " +
                "JOIN student_guardians sg ON sg.student_id = se.student_id " +
                "JOIN guardians g ON g.id = sg.guardian_id WHERE g.user_id = ?",
                (r, n) -> r.getLong(1), userId);
    }

    public Long staffForUser(long userId) {
        List<Long> rows=jdbc.query("SELECT id FROM staff WHERE user_id=? AND status='ACTIVE'",(r,n)->r.getLong(1),userId);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean isTeacherStaff(long staffId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM staff s JOIN user_roles ur ON ur.user_id=s.user_id JOIN roles r ON r.id=ur.role_id WHERE s.id=? AND s.status='ACTIVE' AND r.code='TEACHER'",Integer.class,staffId);
        return count!=null&&count>0;
    }
    public boolean isAssigned(long staffId,long sessionId,long sectionId,long subjectId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM teacher_assignments WHERE staff_id=? AND session_id=? AND section_id=? AND subject_id=? AND status='ACTIVE'",Integer.class,staffId,sessionId,sectionId,subjectId);
        return count!=null&&count>0;
    }
    public boolean isAssignedToSection(long staffId,long sessionId,long sectionId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM teacher_assignments WHERE staff_id=? AND session_id=? AND section_id=? AND status='ACTIVE'",Integer.class,staffId,sessionId,sectionId);
        return count!=null&&count>0;
    }
    public boolean validClassSectionSubject(long classId,long sectionId,long sessionId,long subjectId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM sections sec JOIN class_subjects cs ON cs.class_id=sec.class_id AND cs.session_id=? AND cs.subject_id=? AND cs.is_active=1 WHERE sec.id=? AND sec.class_id=?",Integer.class,sessionId,subjectId,sectionId,classId);
        return count!=null&&count>0;
    }
    public boolean validSectionSubject(long sectionId,long sessionId,long subjectId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM sections sec JOIN class_subjects cs ON cs.class_id=sec.class_id AND cs.session_id=? AND cs.subject_id=? AND cs.is_active=1 WHERE sec.id=?",Integer.class,sessionId,subjectId,sectionId);
        return count!=null&&count>0;
    }
    public boolean exists(String table,long id) {
        String column="id";
        if(!List.of("academic_sessions","classes","sections","subjects","staff","class_subjects","teacher_assignments","timetable","homework","syllabus_progress").contains(table)) throw new IllegalArgumentException("Unsupported table");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Integer.class,id);
        return count!=null&&count>0;
    }

    private long insert(String sql,Object... args) {
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{PreparedStatement ps=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},key);
        Number id=key.getKey(); if(id==null) throw new IllegalStateException("Database did not return an id"); return id.longValue();
    }
    private static LocalDate date(java.sql.ResultSet r,String name)throws java.sql.SQLException{var x=r.getDate(name);return x==null?null:x.toLocalDate();}
    private static LocalDateTime dateTime(java.sql.ResultSet r,String name)throws java.sql.SQLException{Timestamp x=r.getTimestamp(name);return x==null?null:x.toLocalDateTime();}
    private static LocalTime time(java.sql.ResultSet r,String name)throws java.sql.SQLException{var x=r.getTime(name);return x==null?null:x.toLocalTime();}
    private static Integer nullableInt(java.sql.ResultSet r,String name)throws java.sql.SQLException{int x=r.getInt(name);return r.wasNull()?null:x;}
    private static Long nullableLong(java.sql.ResultSet r,String name)throws java.sql.SQLException{long x=r.getLong(name);return r.wasNull()?null:x;}
}
