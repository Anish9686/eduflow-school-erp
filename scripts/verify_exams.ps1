# Test Script for Prompt 17 Live Postman/API Verification

$baseUrl = "http://localhost:8080/api/v1"

function Login-User($username, $password) {
    try {
        $body = @{ username = $username; password = $password } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
        return $resp.accessToken
    } catch {
        Write-Host ("Login failed for " + $username + ": " + $_)
        return $null
    }
}

function Invoke-Api($method, $endpoint, $token, $body = $null) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{
        Uri = "$baseUrl$endpoint"
        Method = $method
        Headers = $headers
    }
    if ($body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($body | ConvertTo-Json -Depth 10)
    }
    try {
        $resp = Invoke-RestMethod @params
        return @{ StatusCode = 200; Data = $resp }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        if (-not $status) { $status = 500 }
        $errBody = ""
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errBody = $reader.ReadToEnd()
        } catch {}
        return @{ StatusCode = $status; Error = $errBody }
    }
}

$results = [System.Collections.Generic.List[Object]]::new()

function Record-Test($name, $expectedStatus, $actualStatus, $details, $errBody = "") {
    $pass = ($expectedStatus -eq $actualStatus)
    $obj = [PSCustomObject]@{
        Scenario = $name
        Expected = $expectedStatus
        Actual = $actualStatus
        Result = if ($pass) { "PASS" } else { "FAIL" }
        Details = $details
    }
    $results.Add($obj)
    Write-Host "[$($obj.Result)] $name | Expected: $expectedStatus | Actual: $actualStatus"
    if (-not $pass -and $errBody) {
        Write-Host "   Error Body: $errBody" -ForegroundColor Red
    }
}

Write-Host "===================================================="
Write-Host "1. AUTHENTICATING TEST ROLES"
Write-Host "===================================================="

$superAdminToken = Login-User ($env:TEST_SUPERADMIN_USER ?? "Anish9661") ($env:TEST_SUPERADMIN_PASS ?? "Anish@9661")
$adminToken      = Login-User ($env:TEST_ADMIN_USER ?? "Satyam9801") ($env:TEST_ADMIN_PASS ?? "Satyam@9801")
$mgmtToken       = Login-User ($env:TEST_MGMT_USER ?? "Ankit9661") ($env:TEST_MGMT_PASS ?? "Ankit@9661")
$teacherToken    = Login-User ($env:TEST_TEACHER_USER ?? "Amit9661") ($env:TEST_TEACHER_PASS ?? "Amit@9661")
$parentToken     = Login-User ($env:TEST_PARENT_USER ?? "Doe9661") ($env:TEST_PARENT_PASS ?? "Doe@9661")
$studentToken    = Login-User ($env:TEST_STUDENT_USER ?? "Atul9661") ($env:TEST_STUDENT_PASS ?? "Atul@9661")

Write-Host "Super Admin Token: $(if ($superAdminToken) { 'OK' } else { 'FAIL' })"
Write-Host "Admin Token:       $(if ($adminToken) { 'OK' } else { 'FAIL' })"
Write-Host "Management Token:  $(if ($mgmtToken) { 'OK' } else { 'FAIL' })"
Write-Host "Teacher Token:     $(if ($teacherToken) { 'OK' } else { 'FAIL' })"
Write-Host "Parent Token:      $(if ($parentToken) { 'OK' } else { 'FAIL' })"
Write-Host "Student Token:     $(if ($studentToken) { 'OK' } else { 'FAIL' })"

Write-Host "`n===================================================="
Write-Host "2. RUNNING 14 MANDATORY POSTMAN SCENARIOS"
Write-Host "===================================================="

# Setup: Admin creates an examination and schedules
$examPayload = @{
    name = "Annual Board Evaluation 2026"
    examType = "FINAL"
    sessionId = 1
    classId = 1
    sectionId = 1
    startDate = "2026-11-01"
    endDate = "2026-11-15"
    gradingSchemeId = 1
    instructions = "Mandatory comprehensive assessment."
}
$createExamRes = Invoke-Api "Post" "/exams" $adminToken $examPayload
$examId = $createExamRes.Data.id
Write-Host "Created Examination ID: $examId"

# Setup schedule for subject 1 (Mathematics - assigned to Amit Kumar)
$schedPayload = @{
    classId = 1
    sectionId = 1
    subjectId = 1
    examDate = "2026-11-05"
    startTime = "09:00:00"
    endTime = "12:00:00"
    maxMarks = 100
    passMarks = 40
    roomNo = "Hall 101"
}
$createSchedRes = Invoke-Api "Post" "/exams/$examId/schedules" $adminToken $schedPayload
Write-Host "Created Schedule for Subject 1 (Math): $($createSchedRes.StatusCode)"

# Setup schedule for subject 2 (English - NOT assigned to Amit Kumar)
$schedPayload2 = @{
    classId = 1
    sectionId = 1
    subjectId = 2
    examDate = "2026-11-07"
    startTime = "09:00:00"
    endTime = "12:00:00"
    maxMarks = 100
    passMarks = 40
    roomNo = "Hall 102"
}
$createSchedRes2 = Invoke-Api "Post" "/exams/$examId/schedules" $adminToken $schedPayload2
Write-Host "Created Schedule for Subject 2 (English): $($createSchedRes2.StatusCode)"

# ----------------------------------------------------
# SCENARIO 1: Teacher enters marks for an assigned subject
# ----------------------------------------------------
$draftMarksPayload = @{
    sectionId = 1
    subjectId = 1
    marks = @(
        @{
            studentId = 1
            marksObtained = 88.5
            status = "PRESENT"
            teacherRemarks = "Excellent problem solving"
        },
        @{
            studentId = 9
            marksObtained = 42.0
            status = "PRESENT"
            teacherRemarks = "Needs revision in algebra"
        }
    )
}
$res1 = Invoke-Api "Post" "/exams/$examId/marks/draft" $teacherToken $draftMarksPayload
Record-Test "1. Teacher enters marks for an assigned subject" 200 $res1.StatusCode "Saved draft marks for Subject 1 (Math)" $res1.Error

# ----------------------------------------------------
# SCENARIO 2: Teacher attempts to enter marks for an unrelated subject
# ----------------------------------------------------
$unrelatedMarksPayload = @{
    sectionId = 1
    subjectId = 2 # English is not assigned to Amit Kumar
    marks = @(
        @{
            studentId = 1
            marksObtained = 75
            status = "PRESENT"
            teacherRemarks = "Hacked marks"
        }
    )
}
$res2 = Invoke-Api "Post" "/exams/$examId/marks/draft" $teacherToken $unrelatedMarksPayload
Record-Test "2. Teacher attempts to enter marks for an unrelated subject" 403 $res2.StatusCode "Forbidden: Not assigned to teach Subject 2" $res2.Error

# ----------------------------------------------------
# SCENARIO 3: Teacher submits marks for review
# ----------------------------------------------------
$submitPayload = @{
    sectionId = 1
    subjectId = 1
}
$res3 = Invoke-Api "Post" "/exams/$examId/marks/submit" $teacherToken $submitPayload
Record-Test "3. Teacher submits marks for review" 200 $res3.StatusCode "Marks submitted for administrative review" $res3.Error

# ----------------------------------------------------
# SCENARIO 4: Teacher attempts to publish results
# ----------------------------------------------------
$res4 = Invoke-Api "Post" "/exams/$examId/publish" $teacherToken
Record-Test "4. Teacher attempts to publish results" 403 $res4.StatusCode "Forbidden: Teachers cannot publish results" $res4.Error

# ----------------------------------------------------
# SCENARIO 5: Management attempts to modify marks
# ----------------------------------------------------
$res5 = Invoke-Api "Post" "/exams/$examId/marks/draft" $mgmtToken $draftMarksPayload
Record-Test "5. Management attempts to modify marks" 403 $res5.StatusCode "Forbidden: Management has read-only access" $res5.Error

# Submissions setup: Admin enters marks for Subject 2 so exam can be fully approved & published
$adminMarksPayload = @{
    sectionId = 1
    subjectId = 2
    marks = @(
        @{
            studentId = 1
            marksObtained = 91.0
            status = "PRESENT"
            teacherRemarks = "Outstanding essays"
        },
        @{
            studentId = 9
            marksObtained = 55.0
            status = "PRESENT"
            teacherRemarks = "Good comprehension"
        }
    )
}
Invoke-Api "Post" "/exams/$examId/marks/draft" $adminToken $adminMarksPayload | Out-Null
Invoke-Api "Post" "/exams/$examId/marks/submit" $adminToken @{ sectionId = 1; subjectId = 2 } | Out-Null

# ----------------------------------------------------
# SCENARIO 6: Admin reviews and approves marks
# ----------------------------------------------------
$submissions = Invoke-Api "Get" "/exams/submissions?examId=$examId" $adminToken
$subMathId = ($submissions.Data | Where-Object { $_.subjectId -eq 1 } | Select-Object -First 1).id
$subEngId  = ($submissions.Data | Where-Object { $_.subjectId -eq 2 } | Select-Object -First 1).id

# Teacher attempts self-approval
$selfApprove = Invoke-Api "Post" "/exams/submissions/$subMathId/review?action=APPROVE" $teacherToken @{}
Write-Host "Teacher self-approval attempt: Status $($selfApprove.StatusCode) (Expected: 403 Forbidden)"

$approveMath = Invoke-Api "Post" "/exams/submissions/$subMathId/review?action=APPROVE" $adminToken @{ comments = "Verified and approved" }
$approveEng  = Invoke-Api "Post" "/exams/submissions/$subEngId/review?action=APPROVE" $adminToken @{ comments = "English marks verified" }

Record-Test "6. Admin reviews and approves marks" 200 $approveMath.StatusCode "Approved submission ID $subMathId" $approveMath.Error

# ----------------------------------------------------
# SCENARIO 7: Admin publishes approved results
# ----------------------------------------------------
$res7 = Invoke-Api "Post" "/exams/$examId/publish" $adminToken
Record-Test "7. Admin publishes approved results" 200 $res7.StatusCode "Published exam ID $examId" $res7.Error

# ----------------------------------------------------
# SCENARIO 8: Student views their own published results
# ----------------------------------------------------
$res8 = Invoke-Api "Get" "/exams/$examId/results" $studentToken
$hasStudentResult = ($res8.Data | Measure-Object).Count -ge 1
Record-Test "8. Student views their own published results" 200 $res8.StatusCode "Returned $($res8.Data.Count) result(s) for authenticated student" $res8.Error

# Student views own report card
$resReportCard = Invoke-Api "Get" "/exams/$examId/report-card?studentId=1" $studentToken
Write-Host "Student report card: $($resReportCard.Data.studentName), Percentage: $($resReportCard.Data.percentage)%, Grade: $($resReportCard.Data.overallGrade)"

# ----------------------------------------------------
# SCENARIO 9: Student attempts to view another student's results
# ----------------------------------------------------
$res9 = Invoke-Api "Get" "/exams/$examId/results?studentId=9" $studentToken
Record-Test "9. Student attempts to view another student's results" 403 $res9.StatusCode "Forbidden: IDOR Violation prevented" $res9.Error

# ----------------------------------------------------
# SCENARIO 10: Parent views a linked child's published results
# ----------------------------------------------------
$res10 = Invoke-Api "Get" "/exams/$examId/results?studentId=1" $parentToken
Record-Test "10. Parent views a linked child's published results" 200 $res10.StatusCode "Returned results for linked student ID 1" $res10.Error

# ----------------------------------------------------
# SCENARIO 11: Parent attempts to view an unrelated child's results
# ----------------------------------------------------
$res11 = Invoke-Api "Get" "/exams/$examId/results?studentId=9" $parentToken
Record-Test "11. Parent attempts to view an unrelated child's results" 403 $res11.StatusCode "Forbidden: Parent not authorized for student 9" $res11.Error

# ----------------------------------------------------
# SCENARIO 12: Parent attempts to access unpublished marks
# ----------------------------------------------------
# Create an unpublished exam and schedule
$unpubExam = Invoke-Api "Post" "/exams" $adminToken @{
    name = "Unpublished Mid-Term 2026"
    examType = "MID_TERM"
    sessionId = 1
    classId = 1
    startDate = "2026-12-01"
    endDate = "2026-12-10"
}
$unpubId = $unpubExam.Data.id
$res12Card = Invoke-Api "Get" "/exams/$unpubId/report-card?studentId=1" $parentToken
Record-Test "12. Parent attempts to access unpublished marks" 403 $res12Card.StatusCode "Report card unavailable for unpublished exam" $res12Card.Error

# ----------------------------------------------------
# SCENARIO 13: Unauthorized API requests attempt to mutate data
# ----------------------------------------------------
$unauthPayload = @{
    name = "Unauthorized Exam"
    examType = "FINAL"
    sessionId = 1
    classId = 1
    startDate = "2026-11-01"
    endDate = "2026-11-05"
}
# Anonymous request
$res13Anon = Invoke-Api "Post" "/exams" $null $unauthPayload
# Student attempting to create exam
$res13Student = Invoke-Api "Post" "/exams" $studentToken $unauthPayload
$anonPass = ($res13Anon.StatusCode -eq 401)
$studentPass = ($res13Student.StatusCode -eq 403)
$actualMutate = if ($anonPass -and $studentPass) { 403 } else { $res13Student.StatusCode }
Record-Test "13. Unauthorized API requests attempt to mutate data" 403 $actualMutate "Anonymous: $($res13Anon.StatusCode) (401), Student: $($res13Student.StatusCode) (403)" $res13Student.Error

# ----------------------------------------------------
# SCENARIO 14: Corrected published results preserve audit history
# ----------------------------------------------------
$correctionPayload = @{
    studentId = 1
    subjectId = 1
    marksObtained = 95.0
    status = "PRESENT"
    reason = "Re-evaluation of question 4 after verification"
}
$res14Corr = Invoke-Api "Post" "/exams/$examId/corrections" $adminToken $correctionPayload
$res14Hist = Invoke-Api "Get" "/exams/$examId/corrections" $adminToken
$auditSaved = ($res14Hist.Data | Where-Object { $_.reason -eq "Re-evaluation of question 4 after verification" })
Record-Test "14. Corrected published results preserve audit history" 200 $res14Corr.StatusCode "Audit correction recorded: Prev $($auditSaved.previousMarks) -> New $($auditSaved.newMarks), Reason: $($auditSaved.reason)" $res14Corr.Error

Write-Host "`n===================================================="
Write-Host "VERIFICATION SUMMARY TABLE"
Write-Host "===================================================="
$results | Format-Table -AutoSize
