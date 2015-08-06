package models.action

import actors.PLMActor
import play.api.libs.json._
import play.api.Logger
import json._
import plm.core.model.lesson.Lecture
import spies.ExecutionSpy

private[action] abstract class GetAction(actor : PLMActor, msg : JsValue) extends Action(actor, msg) {
}

private[action] class GetLessonsAction(actor : PLMActor, msg : JsValue) extends GetAction(actor, msg) {
	override def run() {
		actor.sendMessage("lessons", Json.obj(
				"lessons" -> LessonToJson.lessonsWrite(actor.plm.lessons)
			))
	}
}

private[action] class GetExercisesAction(actor : PLMActor, msg : JsValue) extends GetAction(actor, msg) {
	override def run() {
          if(actor.plm.currentExercise != null) {
            var lectures = actor.plm.game.getCurrentLesson.getRootLectures.toArray(Array[Lecture]())
            actor.sendMessage("exercises", Json.obj(
              "exercises" -> ExerciseToJson.exercisesWrite(lectures) 
            ))
          }
	}
}

private[action] class GetLangsAction(actor : PLMActor, msg : JsValue) extends GetAction(actor, msg) {
	override def run() {
          actor.sendMessage("langs", Json.obj(
            "selected" -> LangToJson.langWrite(actor.currentPreferredLang),
            "availables" -> LangToJson.langsWrite(actor.availableLangs)
          ))
	}
}

private[action] class GetExerciseAction(actor : PLMActor, msg : JsValue) extends GetAction(actor, msg) {
	override def run() {
		var optLessonID: Option[String] = (msg \ "args" \ "lessonID").asOpt[String]
		var optExerciseID: Option[String] = (msg \ "args" \ "exerciseID").asOpt[String]
		var lecture: Lecture = null;
		var executionSpy: ExecutionSpy = new ExecutionSpy(actor, "operations")
		var demoExecutionSpy: ExecutionSpy = new ExecutionSpy(actor, "demoOperations")
		(optLessonID.getOrElse(None), optExerciseID.getOrElse(None)) match {
			case (lessonID:String, exerciseID: String) =>
				lecture = actor.plm.switchExercise(lessonID, exerciseID, executionSpy, demoExecutionSpy)
			case (lessonID:String, _) =>
				lecture = actor.plm.switchLesson(lessonID, executionSpy, demoExecutionSpy)
			case (_, _) =>
				Logger.debug("getExercise: non-correct JSON")
		}
		if(lecture != null) {
			var j : JsValue = Json.obj(
					"exercise" -> LectureToJson.lectureWrites(
							lecture,
							actor.plm.programmingLanguage,
							actor.plm.getStudentCode,
							actor.plm.getInitialWorlds,
							actor.plm.getSelectedWorldID
						)
				)
			actor.sendMessage("exercise", j)
		}
	}
}