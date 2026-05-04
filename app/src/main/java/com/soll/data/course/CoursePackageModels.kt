package com.soll.data.course

data class CoursePackageModel(
    val manifest: CoursePackageManifest,
    val course: CoursePackageCourse,
    val modules: List<CoursePackageModule> = emptyList(),
    val lessons: List<CoursePackageLesson> = emptyList(),
    val day_plans: List<CoursePackageDayPlan> = emptyList(),
    val bonus_sessions: List<CoursePackageSessionPayload> = emptyList(),
    val resources: List<CoursePackageResource> = emptyList(),
)

data class CoursePackageManifest(
    val schema_version: Int,
    val course_slug: String,
    val course_version: String,
    val generated_at: String,
    val source_folder: String,
    val review_status: String,
    val content_quality: String? = null,
)

data class CoursePackageCourse(
    val title: String,
    val description: String,
    val mascot_style: String? = null,
    val notes: List<String> = emptyList(),
)

data class CoursePackageModule(
    val module_key: String,
    val title: String,
    val kind: String,
    val order_index: Int,
    val summary: String,
)

data class CoursePackageLesson(
    val lesson_key: String,
    val module_key: String,
    val order_index: Int,
    val title: String,
    val summary: String,
    val source_name: String,
    val source_path: String? = null,
    val lesson_type: String = "video",
    val session_tag: String? = null,
    val required: Boolean = true,
    val estimated_minutes: Int? = null,
)

data class CoursePackageDayPlan(
    val day_index: Int,
    val title: String,
    val theme: String,
    val morning: CoursePackageSessionPayload? = null,
    val evening: CoursePackageSessionPayload? = null,
    val diary_prompt: CoursePackageDiaryPrompt? = null,
)

data class CoursePackageSessionPayload(
    val lesson_key: String? = null,
    val title: String,
    val summary: String,
    val estimated_minutes: Int? = null,
    val source_name: String? = null,
    val exercises: List<CoursePackageExercise> = emptyList(),
)

data class CoursePackageExercise(
    val title: String,
    val instructions: String,
    val duration_sec: Int? = null,
    val repetitions: Int? = null,
    val rest_sec: Int? = null,
    val difficulty: String? = null,
    val notes: String? = null,
)

data class CoursePackageDiaryPrompt(
    val title: String,
    val fields: List<String> = emptyList(),
)

data class CoursePackageResource(
    val title: String,
    val type: String,
    val source_name: String,
    val source_path: String? = null,
    val summary: String,
    val preview_lines: List<String> = emptyList(),
)
