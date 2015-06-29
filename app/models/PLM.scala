package models

import spies._
import plm.core.model.Game
import plm.core.model.lesson.Lesson
import plm.core.model.lesson.Lecture
import plm.core.model.lesson.Exercise
import plm.core.model.lesson.Exercise.WorldKind
import plm.core.model.lesson.Exercise.StudentOrCorrection
import plm.core.model.lesson.ExecutionProgress
import plm.core.model.lesson.ExecutionProgress._
import plm.core.lang.ProgrammingLanguage
import plm.core.model.session.SourceFile
import plm.core.model.tracking.ProgressSpyListener
import plm.universe.World
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.HashMap
import play.api.libs.json._
import play.api.Logger
import play.api.i18n.Lang
import log.PLMLogger
import java.util.Locale

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.AMQP.BasicProperties;

class PLM(userUUID: String, plmLogger: PLMLogger, locale: Locale, lastProgLang: Option[String], trackUser: Boolean) {
  
  var _currentExercise: Exercise = _
  var _currentLang: Lang = _
  var corrId : String = java.util.UUID.randomUUID().toString();
  var game = new Game(userUUID, plmLogger, locale, lastProgLang.getOrElse("Java"), trackUser)
  
  def lessons: Array[Lesson] = game.getLessons.toArray(Array[Lesson]())

  def switchLesson(lessonID: String, executionSpy: ExecutionSpy, demoExecutionSpy: ExecutionSpy): Lecture = {
    var key = "lessons." + lessonID;
    game.switchLesson(key, true)

    var lect: Lecture = game.getCurrentLesson.getCurrentExercise
    var exo: Exercise = lect.asInstanceOf[Exercise]
    
    addExecutionSpy(exo, executionSpy, WorldKind.CURRENT)
    addExecutionSpy(exo, demoExecutionSpy, WorldKind.ANSWER)
    _currentExercise = exo;
    
    exo.getWorlds(WorldKind.INITIAL).toArray(Array[World]()).foreach { initialWorld: World => 
      initialWorld.setDelay(0)
    }
    
    return lect
  }
  
  def switchExercise(lessonID: String, exerciseID: String, executionSpy: ExecutionSpy, demoExecutionSpy: ExecutionSpy): Lecture = {
    var key = "lessons." + lessonID;
    game.switchLesson(key, true)
    game.switchExercise(exerciseID)

    var lect: Lecture = game.getCurrentLesson.getCurrentExercise
    var exo: Exercise = lect.asInstanceOf[Exercise]
    
    //addExecutionSpy(exo, executionSpy, WorldKind.CURRENT)
    //addExecutionSpy(exo, demoExecutionSpy, WorldKind.ANSWER)
    _currentExercise = exo;

    exo.getWorlds(WorldKind.INITIAL).toArray(Array[World]()).foreach { initialWorld: World => 
      initialWorld.setDelay(0)
    }
    
    return lect
  }
  
  def revertExercise(): Lecture = {
    game.revertExo
    return _currentExercise
  }

  def getSelectedWorldID(): String = {
    return game.getSelectedWorld.getName
  }
  
  def addExecutionSpy(exo: Exercise, spy: ExecutionSpy, kind: WorldKind) {
    // Adding the executionSpy to the current worlds
    exo.getWorlds(kind).toArray(Array[World]()).foreach { world =>
      var worldSpy: ExecutionSpy = spy.clone()
      worldSpy.setWorld(world)
    }
  }
  
  def getInitialWorlds(): Array[World] = {
    if(_currentExercise != null && _currentExercise.getWorlds(WorldKind.INITIAL) != null) _currentExercise.getWorlds(WorldKind.INITIAL).toArray(Array[World]()) else null
  }
  
  
  def runExercise(lessonID: String, exerciseID: String, code: String, workspace: String) {
    Logger.debug("Code:\n"+code)
    _currentExercise.getSourceFile(programmingLanguage, 0).setBody(code)
    if(workspace != null){
      Logger.debug("Workspace:\n"+workspace)
      _currentExercise.getSourceFile(programmingLanguage, 1).setBody(workspace)
    }
    //game.startExerciseExecution()
    askGameLaunch(lessonID, exerciseID, code);
  }
  
  def runDemo(lessonID: String, exerciseID: String) {
    //game.startExerciseDemoExecution()
    //askGameLaunch(); <= prepare new command ?
  }
  
  def askGameLaunch(lessonID:String, exerciseID:String, code:String) {
    // Parameters 
    var QUEUE_NAME_REQUEST : String = "worker_in"
    var QUEUE_NAME_REPLY : String = "worker_out"
    
    // This part handles compilation with workers.
    // Properties
    var props : BasicProperties = new BasicProperties.Builder().correlationId(corrId).replyTo(QUEUE_NAME_REPLY).build()
    System.out.println(corrId)
    // Connection
    var factory : ConnectionFactory = new ConnectionFactory()
    factory.setHost("localhost")
    var connection : Connection  = factory.newConnection()
    var channelOut : Channel = connection.createChannel()
    var channelIn : Channel = connection.createChannel()
    channelOut.queueDeclare(QUEUE_NAME_REQUEST, false, false, false, null)
    channelIn.queueDeclare(QUEUE_NAME_REPLY, false, false, false, null)
    //Request
    var msg : String = ""
    msg += "{ \"lesson\" : \"" + lessonID + "\", " +
      "exercise : \"" + exerciseID + "\"}"
    channelOut.basicPublish("", QUEUE_NAME_REQUEST, props,
        msg.getBytes("UTF-8"))
    // Reply
    var consumer : QueueingConsumer = new QueueingConsumer(channelIn)
    channelIn.basicConsume(QUEUE_NAME_REPLY, true, consumer)
    var state: Boolean = true;
    while(state) {
      var delivery : QueueingConsumer.Delivery = consumer.nextDelivery()
      if (delivery.getProperties().getCorrelationId().equals(corrId)) {
        var message : String = new String(delivery.getBody(), "UTF-8");
        var p : JSONParser = new JSONParser();
        try {
          var replyJSON: JSONObject = p.parse(message).asInstanceOf[JSONObject];
          var r : String = replyJSON.get("msgType").toString();
          if(r.equals("0") || r.equals("1")) {
            state = false;
          }
        } catch {
          case e : ParseException => // NO OP
        }
      }
    }
  }
  
  
  def stopExecution() {
    //game.stopExerciseExecution()
    // NO OP ?
  }
  
  def programmingLanguage: ProgrammingLanguage = game.getProgrammingLanguage
  
  def setProgrammingLanguage(lang: String) {
    game.setProgrammingLanguage(lang)
  }
  
  def getStudentCode: String = {
    if(_currentExercise != null && _currentExercise.getSourceFile(programmingLanguage, programmingLanguage.getVisualIndex()) != null) _currentExercise.getSourceFile(programmingLanguage, programmingLanguage.getVisualIndex()).getBody else ""
  }
  
  def addProgressSpyListener(progressSpyListener: ProgressSpyListener) {
    game.addProgressSpyListener(progressSpyListener)  
  }
  
  def removeProgressSpyListener(progressSpyListener: ProgressSpyListener) {
    game.removeProgressSpyListener(progressSpyListener)  
  }

  def setLang(lang: Lang) {
  	if(_currentLang != lang) {
  		_currentLang = lang
  		game.setLocale(_currentLang.toLocale)
  	}
  }

  def currentExercise: Exercise = _currentExercise
  
  def getMission(progLang: ProgrammingLanguage): String = {
    if(_currentExercise != null) _currentExercise.getMission(progLang) else ""
  }
  
  def setUserUUID(userUUID: String) {
    _currentExercise = null
    game.setUserUUID(userUUID)
  }
  
  def signalIdle(start: String, end: String, duration: String) {
    game.signalIdle(start, end, duration)
  }
  
  def setTrackUser(trackUser: Boolean) {
    game.setTrackUser(trackUser)
  }
}