package com.erp.portal.academic;

import com.erp.portal.academic.AcademicDtos.*;
import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AcademicService {
    private static final Set<String> SYLLABUS_STATUS=Set.of("PENDING","IN_PROGRESS","COMPLETED");
    private final AcademicRepository repo;
    private final AuditService audit;
    public AcademicService(AcademicRepository repo,AuditService audit){this.repo=repo;this.audit=audit;}

    @Transactional(readOnly=true) public List<SessionResponse> sessions(){return repo.sessions();}
    @Transactional public SessionResponse createSession(SessionRequest x,SecurityPrincipal actor,HttpServletRequest request){validateSession(x);try{long id=repo.createSession(x);audit.record("ACADEMIC_SESSION_CREATED","ACADEMIC_SESSION",Long.toString(id),actor,null,null,Map.of("code",x.code(),"active",x.active()),request);return item(repo.session(id),"Academic session");}catch(DataIntegrityViolationException e){throw AcademicException.conflict("code","Session code must be unique");}}
    @Transactional public SessionResponse updateSession(long id,SessionRequest x,SecurityPrincipal actor,HttpServletRequest request){validateSession(x);require("academic_sessions",id,"Academic session");try{repo.updateSession(id,x);audit.record("ACADEMIC_SESSION_UPDATED","ACADEMIC_SESSION",Long.toString(id),actor,null,null,Map.of("code",x.code(),"active",x.active()),request);return item(repo.session(id),"Academic session");}catch(DataIntegrityViolationException e){throw AcademicException.conflict("code","Session code must be unique");}}
    @Transactional public SessionResponse sessionStatus(long id,boolean active,SecurityPrincipal actor,HttpServletRequest request){require("academic_sessions",id,"Academic session");repo.setSessionActive(id,active);audit.record("ACADEMIC_SESSION_STATUS_CHANGED","ACADEMIC_SESSION",Long.toString(id),actor,null,null,Map.of("active",active),request);return item(repo.session(id),"Academic session");}
    private static void validateSession(SessionRequest x){if(!x.startDate().isBefore(x.endDate()))throw AcademicException.invalid("endDate","must be after startDate");}

    @Transactional(readOnly=true) public List<ClassResponse> classes(){return repo.classes();}
    @Transactional public ClassResponse createClass(ClassRequest x,SecurityPrincipal actor,HttpServletRequest req){long id;try{id=repo.createClass(x);}catch(DataIntegrityViolationException e){throw AcademicException.conflict("name","Class name must be unique");}audit.record("ACADEMIC_CLASS_CREATED","CLASS",Long.toString(id),actor,null,null,Map.of("name",x.name()),req);return repo.classes().stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Class"));}
    @Transactional public ClassResponse updateClass(long id,ClassRequest x,SecurityPrincipal actor,HttpServletRequest req){require("classes",id,"Class");try{repo.updateClass(id,x);}catch(DataIntegrityViolationException e){throw AcademicException.conflict("name","Class name must be unique");}audit.record("ACADEMIC_CLASS_UPDATED","CLASS",Long.toString(id),actor,null,null,Map.of("name",x.name()),req);return repo.classes().stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Class"));}

    @Transactional(readOnly=true) public List<SectionResponse> sections(Long classId){ return sections(classId, null); }
    @Transactional(readOnly=true) public List<SectionResponse> sections(Long classId, SecurityPrincipal actor){
        if(classId!=null)require("classes",classId,"Class");
        List<Long> allowed = allowedSectionsForActor(actor);
        if (allowed != null && allowed.isEmpty()) return List.of();
        return repo.sections(classId, allowed);
    }
    @Transactional public SectionResponse createSection(SectionRequest x,SecurityPrincipal actor,HttpServletRequest req){require("classes",x.classId(),"Class");try{long id=repo.createSection(x);audit.record("ACADEMIC_SECTION_CREATED","SECTION",Long.toString(id),actor,null,null,Map.of("classId",x.classId(),"name",x.name()),req);return repo.sections(x.classId()).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Section"));}catch(DataIntegrityViolationException e){throw AcademicException.conflict("name","Section name already exists in this class");}}
    @Transactional public SectionResponse updateSection(long id,SectionRequest x,SecurityPrincipal actor,HttpServletRequest req){require("sections",id,"Section");require("classes",x.classId(),"Class");if(repo.sectionClassId(id)!=x.classId()&&repo.sectionHasAcademicRecords(id))throw AcademicException.conflict("classId","A section with assignments or academic records cannot be moved to another class");try{repo.updateSection(id,x);}catch(DataIntegrityViolationException e){throw AcademicException.conflict("name","Section name already exists in this class");}audit.record("ACADEMIC_SECTION_UPDATED","SECTION",Long.toString(id),actor,null,null,Map.of("classId",x.classId(),"name",x.name()),req);return repo.sections(x.classId()).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Section"));}

    @Transactional(readOnly=true) public List<SubjectResponse> subjects(){return repo.subjects();}
    @Transactional public SubjectResponse createSubject(SubjectRequest x,SecurityPrincipal actor,HttpServletRequest req){try{long id=repo.createSubject(x);audit.record("ACADEMIC_SUBJECT_CREATED","SUBJECT",Long.toString(id),actor,null,null,Map.of("code",x.code()),req);return repo.subjects().stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Subject"));}catch(DataIntegrityViolationException e){throw AcademicException.conflict("code","Subject code must be unique");}}
    @Transactional public SubjectResponse updateSubject(long id,SubjectRequest x,SecurityPrincipal actor,HttpServletRequest req){require("subjects",id,"Subject");try{repo.updateSubject(id,x);}catch(DataIntegrityViolationException e){throw AcademicException.conflict("code","Subject code must be unique");}audit.record("ACADEMIC_SUBJECT_UPDATED","SUBJECT",Long.toString(id),actor,null,null,Map.of("code",x.code()),req);return repo.subjects().stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Subject"));}

    @Transactional(readOnly=true) public List<ClassSubjectResponse> classSubjects(Long session,Long clazz){return repo.classSubjects(session,clazz);}
    @Transactional public ClassSubjectResponse assignSubject(ClassSubjectRequest x,SecurityPrincipal actor,HttpServletRequest req){require("academic_sessions",x.sessionId(),"Academic session");require("classes",x.classId(),"Class");require("subjects",x.subjectId(),"Subject");try{long id=repo.createClassSubject(x);audit.record("CLASS_SUBJECT_ASSIGNED","CLASS_SUBJECT",Long.toString(id),actor,null,null,Map.of("sessionId",x.sessionId(),"classId",x.classId(),"subjectId",x.subjectId()),req);return repo.classSubjects(x.sessionId(),x.classId()).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Class subject assignment"));}catch(DataIntegrityViolationException e){throw AcademicException.conflict("subjectId","Subject is already assigned to this class and session");}}
    @Transactional public void deactivateClassSubject(long id,SecurityPrincipal actor,HttpServletRequest req){require("class_subjects",id,"Class subject assignment");if(repo.classSubjectInUse(id))throw AcademicException.conflict("id","Remove dependent teacher assignments and timetable entries before deactivating this subject");repo.deactivateClassSubject(id);audit.record("CLASS_SUBJECT_DEACTIVATED","CLASS_SUBJECT",Long.toString(id),actor,null,null,Map.of("active",false),req);}

    @Transactional(readOnly=true) public List<TeacherAssignmentResponse> teacherAssignments(Long session,SecurityPrincipal actor){Long staff=teacherStaffScope(actor);return repo.teacherAssignments(session,staff);}
    @Transactional(readOnly=true) public List<TeacherOption> teachers(){return repo.teachers();}
    @Transactional public TeacherAssignmentResponse assignTeacher(TeacherAssignmentRequest x,SecurityPrincipal actor,HttpServletRequest req){require("academic_sessions",x.sessionId(),"Academic session");require("sections",x.sectionId(),"Section");require("subjects",x.subjectId(),"Subject");if(!repo.validSectionSubject(x.sectionId(),x.sessionId(),x.subjectId()))throw AcademicException.invalid("subjectId","must be assigned to the section's class for this session");if(!repo.isTeacherStaff(x.staffId()))throw AcademicException.invalid("staffId","must identify active teaching staff linked to a TEACHER account");try{long id=repo.createTeacherAssignment(x);audit.record("TEACHER_ASSIGNED","TEACHER_ASSIGNMENT",Long.toString(id),actor,null,null,Map.of("sessionId",x.sessionId(),"sectionId",x.sectionId(),"subjectId",x.subjectId(),"staffId",x.staffId()),req);return repo.teacherAssignments(x.sessionId(),null).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Teacher assignment"));}catch(DataIntegrityViolationException e){throw AcademicException.conflict("staffId","This teacher is already assigned to the section, subject and session");}}
    @Transactional public void deactivateTeacher(long id,SecurityPrincipal actor,HttpServletRequest req){require("teacher_assignments",id,"Teacher assignment");repo.deactivateTeacherAssignment(id);audit.record("TEACHER_ASSIGNMENT_DEACTIVATED","TEACHER_ASSIGNMENT",Long.toString(id),actor,null,null,Map.of("status","INACTIVE"),req);}

    @Transactional(readOnly=true) public List<TimetableResponse> timetable(Long session,Long section,SecurityPrincipal actor){
        List<Long> allowed = allowedSectionsForActor(actor);
        if (allowed != null) {
            if (allowed.isEmpty()) return List.of();
            if (section != null) {
                if (!allowed.contains(section)) throw AcademicException.forbidden();
            } else if (allowed.size() == 1) {
                section = allowed.getFirst();
            }
        }
        return repo.timetable(session,section,teacherStaffScope(actor),allowed);
    }
    @Transactional public TimetableResponse addTimetable(TimetableRequest x,SecurityPrincipal actor,HttpServletRequest req){validateTimetable(x,null);repo.lockSession(x.sessionId());if(repo.hasTimetableConflict(x,null))throw AcademicException.conflict("time","The section or teacher already has an overlapping timetable entry");try{long id=repo.createTimetable(x);audit.record("TIMETABLE_CREATED","TIMETABLE",Long.toString(id),actor,null,null,Map.of("sessionId",x.sessionId(),"sectionId",x.sectionId(),"dayOfWeek",x.dayOfWeek()),req);return repo.timetable(x.sessionId(),x.sectionId(),null).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Timetable entry"));}catch(DataIntegrityViolationException e){throw AcademicException.conflict("time","The section or teacher already has an entry at this time");}}
    @Transactional public TimetableResponse updateTimetable(long id,TimetableRequest x,SecurityPrincipal actor,HttpServletRequest req){require("timetable",id,"Timetable entry");validateTimetable(x,id);repo.lockSession(x.sessionId());if(repo.hasTimetableConflict(x,id))throw AcademicException.conflict("time","The section or teacher already has an overlapping timetable entry");repo.updateTimetable(id,x);audit.record("TIMETABLE_UPDATED","TIMETABLE",Long.toString(id),actor,null,null,Map.of("sessionId",x.sessionId(),"sectionId",x.sectionId(),"dayOfWeek",x.dayOfWeek()),req);return repo.timetable(x.sessionId(),x.sectionId(),null).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Timetable entry"));}
    @Transactional public void deleteTimetable(long id,SecurityPrincipal actor,HttpServletRequest req){require("timetable",id,"Timetable entry");repo.deleteTimetable(id);audit.record("TIMETABLE_DELETED","TIMETABLE",Long.toString(id),actor,null,null,Map.of("deleted",true),req);}
    private void validateTimetable(TimetableRequest x,Long ignored){require("academic_sessions",x.sessionId(),"Academic session");require("classes",x.classId(),"Class");require("sections",x.sectionId(),"Section");require("subjects",x.subjectId(),"Subject");if(!x.startTime().isBefore(x.endTime()))throw AcademicException.invalid("endTime","must be after startTime");if(!repo.validClassSectionSubject(x.classId(),x.sectionId(),x.sessionId(),x.subjectId()))throw AcademicException.invalid("sectionId","class, section and subject assignment do not match");if(x.teacherId()!=null){if(!repo.isTeacherStaff(x.teacherId()))throw AcademicException.invalid("teacherId","must identify active teaching staff linked to a TEACHER account");if(!repo.isAssigned(x.teacherId(),x.sessionId(),x.sectionId(),x.subjectId()))throw AcademicException.invalid("teacherId","teacher must be assigned to this section, subject and session");}}

    @Transactional(readOnly=true) public List<HomeworkResponse> homework(Long session,Long section,SecurityPrincipal actor){
        List<Long> allowed = allowedSectionsForActor(actor);
        if (allowed != null) {
            if (allowed.isEmpty()) return List.of();
            if (section != null) {
                if (!allowed.contains(section)) throw AcademicException.forbidden();
            } else if (allowed.size() == 1) {
                section = allowed.getFirst();
            }
        }
        Long staff=teacherStaffScope(actor);
        return repo.homework(session,section,staff,allowed);
    }
    @Transactional public HomeworkResponse createHomework(HomeworkRequest x,SecurityPrincipal actor,HttpServletRequest req){require("academic_sessions",x.sessionId(),"Academic session");require("sections",x.sectionId(),"Section");require("subjects",x.subjectId(),"Subject");if(!repo.validSectionSubject(x.sectionId(),x.sessionId(),x.subjectId()))throw AcademicException.invalid("subjectId","must be assigned to this section for the selected session");Long scoped=teacherStaffScope(actor);boolean manager=actor.permissions().contains("HOMEWORK:MANAGE");if(scoped==null&&manager)scoped=repo.staffForUser(actor.userId());if(scoped==null)throw AcademicException.forbidden();if(!manager&&!repo.isAssigned(scoped,x.sessionId(),x.sectionId(),x.subjectId()))throw AcademicException.forbidden();long id=repo.createHomework(x,scoped);audit.record("HOMEWORK_CREATED","HOMEWORK",Long.toString(id),actor,null,null,Map.of("sessionId",x.sessionId(),"sectionId",x.sectionId(),"subjectId",x.subjectId()),req);return repo.homework(x.sessionId(),x.sectionId(),null).stream().filter(a->a.id()==id).findFirst().orElseThrow(()->AcademicException.notFound("Homework"));}
    @Transactional public void deleteHomework(long id,SecurityPrincipal actor,HttpServletRequest req){Long scope=teacherStaffScope(actor);if(scope==null&&!actor.permissions().contains("HOMEWORK:MANAGE"))throw AcademicException.forbidden();if(repo.deleteHomework(id,scope)!=1)throw AcademicException.notFound("Homework");audit.record("HOMEWORK_DELETED","HOMEWORK",Long.toString(id),actor,null,null,Map.of("deleted",true),req);}

    @Transactional(readOnly=true) public List<SyllabusResponse> syllabus(Long session,Long section,Long subject,SecurityPrincipal actor){
        List<Long> allowed = allowedSectionsForActor(actor);
        if (allowed != null) {
            if (allowed.isEmpty()) return List.of();
            if (section != null) {
                if (!allowed.contains(section)) throw AcademicException.forbidden();
            } else if (allowed.size() == 1) {
                section = allowed.getFirst();
            }
        }
        Long staff=teacherStaffScope(actor);
        return repo.syllabus(session,section,subject,staff,allowed);
    }
    @Transactional public SyllabusResponse createSyllabus(SyllabusRequest x,SecurityPrincipal actor,HttpServletRequest req){validateSyllabus(x);Long staff=teacherStaffScope(actor);boolean manager=actor.permissions().contains("SYLLABUS:MANAGE");if(staff==null&&manager)staff=repo.staffForUser(actor.userId());if(staff==null||(!manager&&!repo.isAssigned(staff,x.sessionId(),x.sectionId(),x.subjectId())))throw AcademicException.forbidden();long id=repo.createSyllabus(x,staff);audit.record("SYLLABUS_PROGRESS_RECORDED","SYLLABUS_PROGRESS",Long.toString(id),actor,null,null,Map.of("status",x.status(),"topic",x.topic()),req);return item(repo.syllabusItem(id),"Syllabus progress");}
    @Transactional public SyllabusResponse updateSyllabus(long id,SyllabusRequest x,SecurityPrincipal actor,HttpServletRequest req){validateSyllabus(x);var old=item(repo.syllabusItem(id),"Syllabus progress");Long staff=teacherStaffScope(actor);boolean manager=actor.permissions().contains("SYLLABUS:MANAGE");if(staff==null&&manager)staff=repo.staffForUser(actor.userId());if(staff==null||(!manager&&!repo.isAssigned(staff,x.sessionId(),x.sectionId(),x.subjectId())))throw AcademicException.forbidden();repo.updateSyllabus(id,x);audit.record("SYLLABUS_PROGRESS_UPDATED","SYLLABUS_PROGRESS",Long.toString(id),actor,null,Map.of("status",old.status()),Map.of("status",x.status()),req);return item(repo.syllabusItem(id),"Syllabus progress");}
    private void validateSyllabus(SyllabusRequest x){if(!SYLLABUS_STATUS.contains(x.status()))throw AcademicException.invalid("status","must be PENDING, IN_PROGRESS or COMPLETED");require("academic_sessions",x.sessionId(),"Academic session");require("sections",x.sectionId(),"Section");require("subjects",x.subjectId(),"Subject");if(!repo.validSectionSubject(x.sectionId(),x.sessionId(),x.subjectId()))throw AcademicException.invalid("subjectId","must be assigned to this section for the selected session");}

    private List<Long> allowedSectionsForActor(SecurityPrincipal actor) {
        if (actor == null) return null;
        if (actor.roles().contains("STUDENT")) {
            return repo.studentSectionsForUser(actor.userId());
        }
        if (actor.roles().contains("PARENT_GUARDIAN")) {
            return repo.parentSectionsForUser(actor.userId());
        }
        return null;
    }

    private Long teacherStaffScope(SecurityPrincipal actor){if(!actor.roles().contains("TEACHER"))return null;Long staff=repo.staffForUser(actor.userId());if(staff==null)throw AcademicException.forbidden();return staff;}
    private void require(String table,long id,String item){if(!repo.exists(table,id))throw AcademicException.notFound(item);}
    private static <T>T item(java.util.Optional<T> row,String name){return row.orElseThrow(()->AcademicException.notFound(name));}
}
