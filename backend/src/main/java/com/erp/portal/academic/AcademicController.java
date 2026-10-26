package com.erp.portal.academic;

import com.erp.portal.academic.AcademicDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/academics")
public class AcademicController {
    private final AcademicService service;
    public AcademicController(AcademicService service){this.service=service;}

    @GetMapping("/sessions") @PreAuthorize("hasAnyAuthority('ACADEMIC:READ','TIMETABLE:READ','HOMEWORK:READ','SYLLABUS:READ')")
    public List<SessionResponse> sessions(){return service.sessions();}
    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')")
    public SessionResponse createSession(@Valid @RequestBody SessionRequest x,@AuthenticationPrincipal SecurityPrincipal actor,HttpServletRequest req){return service.createSession(x,actor,req);}
    @PutMapping("/sessions/{id}") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')")
    public SessionResponse updateSession(@PathVariable @Min(1) long id,@Valid @RequestBody SessionRequest x,@AuthenticationPrincipal SecurityPrincipal actor,HttpServletRequest req){return service.updateSession(id,x,actor,req);}
    @PatchMapping("/sessions/{id}/status") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')")
    public SessionResponse sessionStatus(@PathVariable @Min(1) long id,@Valid @RequestBody StatusRequest x,@AuthenticationPrincipal SecurityPrincipal actor,HttpServletRequest req){return service.sessionStatus(id,x.active(),actor,req);}

    @GetMapping("/classes") @PreAuthorize("hasAuthority('ACADEMIC:READ')") public List<ClassResponse> classes(){return service.classes();}
    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public ClassResponse createClass(@Valid @RequestBody ClassRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.createClass(x,a,r);}
    @PutMapping("/classes/{id}") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public ClassResponse updateClass(@PathVariable @Min(1) long id,@Valid @RequestBody ClassRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.updateClass(id,x,a,r);}

    @GetMapping("/sections") @PreAuthorize("hasAnyAuthority('ACADEMIC:READ','TIMETABLE:READ','HOMEWORK:READ','SYLLABUS:READ')") public List<SectionResponse> sections(@RequestParam(required=false) @Min(1) Long classId,@AuthenticationPrincipal SecurityPrincipal a){return service.sections(classId,a);}
    @PostMapping("/sections") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public SectionResponse createSection(@Valid @RequestBody SectionRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.createSection(x,a,r);}
    @PutMapping("/sections/{id}") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public SectionResponse updateSection(@PathVariable @Min(1) long id,@Valid @RequestBody SectionRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.updateSection(id,x,a,r);}

    @GetMapping("/subjects") @PreAuthorize("hasAnyAuthority('ACADEMIC:READ','TIMETABLE:READ','HOMEWORK:READ','SYLLABUS:READ')") public List<SubjectResponse> subjects(){return service.subjects();}
    @PostMapping("/subjects") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public SubjectResponse createSubject(@Valid @RequestBody SubjectRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.createSubject(x,a,r);}
    @PutMapping("/subjects/{id}") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public SubjectResponse updateSubject(@PathVariable @Min(1) long id,@Valid @RequestBody SubjectRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.updateSubject(id,x,a,r);}

    @GetMapping("/class-subjects") @PreAuthorize("hasAuthority('ACADEMIC:READ')") public List<ClassSubjectResponse> classSubjects(@RequestParam(required=false) @Min(1) Long sessionId,@RequestParam(required=false) @Min(1) Long classId){return service.classSubjects(sessionId,classId);}
    @PostMapping("/class-subjects") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public ClassSubjectResponse assignSubject(@Valid @RequestBody ClassSubjectRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.assignSubject(x,a,r);}
    @DeleteMapping("/class-subjects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public void deactivateClassSubject(@PathVariable @Min(1) long id,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){service.deactivateClassSubject(id,a,r);}

    @GetMapping("/teacher-assignments") @PreAuthorize("hasAuthority('ACADEMIC:READ')") public List<TeacherAssignmentResponse> teacherAssignments(@RequestParam(required=false) @Min(1) Long sessionId,@AuthenticationPrincipal SecurityPrincipal a){return service.teacherAssignments(sessionId,a);}
    @GetMapping("/teachers") @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public List<TeacherOption> teachers(){return service.teachers();}
    @PostMapping("/teacher-assignments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public TeacherAssignmentResponse assignTeacher(@Valid @RequestBody TeacherAssignmentRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.assignTeacher(x,a,r);}
    @DeleteMapping("/teacher-assignments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('ACADEMIC:MANAGE')") public void deactivateTeacher(@PathVariable @Min(1) long id,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){service.deactivateTeacher(id,a,r);}

    @GetMapping("/timetable") @PreAuthorize("hasAnyAuthority('ACADEMIC:READ','TIMETABLE:READ')") public List<TimetableResponse> timetable(@RequestParam(required=false) @Min(1) Long sessionId,@RequestParam(required=false) @Min(1) Long sectionId,@AuthenticationPrincipal SecurityPrincipal a){return service.timetable(sessionId,sectionId,a);}
    @PostMapping("/timetable") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('TIMETABLE:MANAGE')") public TimetableResponse addTimetable(@Valid @RequestBody TimetableRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.addTimetable(x,a,r);}
    @PutMapping("/timetable/{id}") @PreAuthorize("hasAuthority('TIMETABLE:MANAGE')") public TimetableResponse updateTimetable(@PathVariable @Min(1) long id,@Valid @RequestBody TimetableRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.updateTimetable(id,x,a,r);}
    @DeleteMapping("/timetable/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('TIMETABLE:MANAGE')") public void deleteTimetable(@PathVariable @Min(1) long id,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){service.deleteTimetable(id,a,r);}

    @GetMapping("/homework") @PreAuthorize("hasAnyAuthority('HOMEWORK:READ','HOMEWORK:CREATE','HOMEWORK:MANAGE')") public List<HomeworkResponse> homework(@RequestParam(required=false) @Min(1) Long sessionId,@RequestParam(required=false) @Min(1) Long sectionId,@AuthenticationPrincipal SecurityPrincipal a){return service.homework(sessionId,sectionId,a);}
    @PostMapping("/homework") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyAuthority('HOMEWORK:CREATE','HOMEWORK:MANAGE')") public HomeworkResponse createHomework(@Valid @RequestBody HomeworkRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.createHomework(x,a,r);}
    @DeleteMapping("/homework/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyAuthority('HOMEWORK:CREATE','HOMEWORK:MANAGE')") public void deleteHomework(@PathVariable @Min(1) long id,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){service.deleteHomework(id,a,r);}

    @GetMapping("/syllabus") @PreAuthorize("hasAnyAuthority('SYLLABUS:READ','SYLLABUS:UPDATE','SYLLABUS:MANAGE')") public List<SyllabusResponse> syllabus(@RequestParam(required=false) @Min(1) Long sessionId,@RequestParam(required=false) @Min(1) Long sectionId,@RequestParam(required=false) @Min(1) Long subjectId,@AuthenticationPrincipal SecurityPrincipal a){return service.syllabus(sessionId,sectionId,subjectId,a);}
    @PostMapping("/syllabus") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyAuthority('SYLLABUS:UPDATE','SYLLABUS:MANAGE')") public SyllabusResponse createSyllabus(@Valid @RequestBody SyllabusRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.createSyllabus(x,a,r);}
    @PutMapping("/syllabus/{id}") @PreAuthorize("hasAnyAuthority('SYLLABUS:UPDATE','SYLLABUS:MANAGE')") public SyllabusResponse updateSyllabus(@PathVariable @Min(1) long id,@Valid @RequestBody SyllabusRequest x,@AuthenticationPrincipal SecurityPrincipal a,HttpServletRequest r){return service.updateSyllabus(id,x,a,r);}

    public record StatusRequest(boolean active) { }
}
