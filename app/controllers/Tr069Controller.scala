package controllers

import io.kanaka.monadic.dsl._
import akka.Done
import models._
import play.api.Logging
import play.api.cache.AsyncCacheApi
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{ProfileService, UnitService, UnitTypeService}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Node

class Tr069Controller(
    cc: ControllerComponents,
    unitService: UnitService,
    profileService: ProfileService,
    unitTypeService: UnitTypeService,
    cache: AsyncCacheApi,
    secureAction: SecureAction
)(
    implicit ec: ExecutionContext
) extends AbstractController(cc)
    with I18nSupport
    with Logging {

  def provision = secureAction.authenticate.async { implicit request =>
    for {
      sessionData <- getSessionData(request.sessionId) ?| InternalServerError("Failed to get session")
      result      <- processRequest(sessionData, payload(request)) ?| (error => InternalServerError(error))
      _           <- putSessionData(request.sessionId, sessionData, result._1) ?| InternalServerError("Failed to update session")
    } yield result._2.withSession(request.session)
  }

  private def payload(request: SecureRequest[AnyContent]) =
    request.body.asXml.flatMap(_.headOption).getOrElse(<Empty />)

  private def processRequest(sessionData: SessionData, payload: Node): Future[Either[String, (SessionData, Result)]] = {
    val method = CwmpMethod.fromNode(payload).getOrElse(CwmpMethod.EM)
    (method match {
      case CwmpMethod.IN =>
        Future.successful(
          for {
            withHeader   <- getHeader(sessionData, payload)
            withDeviceId <- getDeviceId(withHeader, payload)
            withEvents   <- getEvents(withDeviceId, payload)
            sessionData  <- getParams(withEvents, payload)
          } yield {
            val unitId = sessionData.unit.map(_.unitId).getOrElse("N/A")
            logger.warn(s"Got the following Inform from unit: $unitId:\n$sessionData")
            val result = Ok(
              <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soapenv:Body>
                  <cwmp:InformResponse xmlns:cwmp="urn:dslforum-org:cwmp-1-0">
                    <MaxEnvelopes>1</MaxEnvelopes>
                  </cwmp:InformResponse>
                </soapenv:Body>
              </soapenv:Envelope>
            ).withHeaders("SOAPAction" -> "")
            (sessionData, result)
          }
        )
      case otherMethod =>
        logger.warn(s"Got ${otherMethod.abbr} method, answering with NotImplemented")
        Future.successful(Right((sessionData, NotImplemented)))
    }).map(_.map {
      case (updatedSessionData, result) =>
        (updatedSessionData.copy(requests = updatedSessionData.requests ++ Seq(method)), result)
    })
  }

  private def getHeader(sessionData: SessionData, payload: Node): Either[String, SessionData] =
    HeaderStruct.fromNode(payload) match {
      case Some(header) => Right(sessionData.copy(header = Some(header)))
      case None         => Left("Missing header in payload")
    }

  private def getEvents(sessionData: SessionData, payload: Node): Either[String, SessionData] =
    EventStruct.fromNode(payload) match {
      case seq: Seq[EventStruct] if seq.nonEmpty => Right(sessionData.copy(events = seq))
      case _                                     => Left("Missing events")
    }

  private def getParams(sessionData: SessionData, payload: Node): Either[String, SessionData] =
    ParameterValueStruct.fromNode(payload) match {
      case seq: Seq[ParameterValueStruct] if seq.nonEmpty => Right(sessionData.copy(params = seq))
      case _                                              => Left("Missing params")
    }

  private def getDeviceId(sessionData: SessionData, payload: Node): Either[String, SessionData] =
    DeviceIdStruct.fromNode(payload) match {
      case Some(deviceIdStruct) => Right(sessionData.copy(deviceId = Some(deviceIdStruct)))
      case None                 => Left("Missing deviceId")
    }

  private def getSessionData(sessionId: String): Future[SessionData] =
    cache.getOrElseUpdate[SessionData](sessionDataKey(sessionId)) {
      Future.successful(SessionData(sessionId))
    }

  private def putSessionData(sessionId: String, sessionData: SessionData, updateSessionData: SessionData): Future[Done] =
    if (sessionData != updateSessionData)
      cache.set(sessionDataKey(sessionId), updateSessionData)
    else
      Future.successful(Done)

  private def sessionDataKey(sessionId: String) = s"$sessionId-data"
}
