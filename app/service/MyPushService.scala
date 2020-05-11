package service

import java.security.Security
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import model.Evento
import nl.martijndwars.webpush.Subscription.Keys
import nl.martijndwars.webpush.{Notification, PushService, Subscription}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import play.api.cache.redis.CacheAsyncApi
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class MyPushService @Inject()(actorSystem: ActorSystem,
                             ws: WSClient,
                             cache: CacheAsyncApi,
                             configuration: Configuration
                           )
                             (implicit ec: ExecutionContext) {

  Security.addProvider(new BouncyCastleProvider)

  val logger = Logger(getClass)
  val vapidPublicKey = configuration.get[String]("vapid-public-key")
  val vapidPrivateKey = configuration.get[String]("vapid-private-key")

  def fmtCacheKey(liveId: String, authKey: String) = s"push-$liveId:$authKey"

  def subscribe(liveId: String, subscriptionJson: JsValue) = {
    logger.warn(s"request body json [${subscriptionJson}]")
    val authkey = (subscriptionJson \ "keys" \ "auth").as[String]
    val pushKey = fmtCacheKey(liveId, authkey)
    cache.set(pushKey, subscriptionJson.toString)
  }

  def notify(liveId: String, evento: Evento) = {
    if (evento.data.isAfter(LocalDateTime.now.minusMinutes(10))) {
      for {
        cacheKeys <- cache.matching(fmtCacheKey(liveId, "*"))
        optSubscriptions <- Future.sequence(cacheKeys.map(cacheKey => cache.get[String](cacheKey)))
      } yield {
        val subscriptions = optSubscriptions.flatten
        logger.warn(s"Notificando [${subscriptions.length}] para [${evento.nome}]")
        subscriptions.foreach(subscriptionStr => {
          val subscriptionJson = Json.parse(subscriptionStr)
          val endpoint = (subscriptionJson \ "endpoint").as[String]
          val p256dh = (subscriptionJson \ "keys" \ "p256dh").as[String]
          val auth = (subscriptionJson \ "keys" \ "auth").as[String]
          val keys = new Keys(p256dh, auth)
          val subscription = new Subscription(endpoint, keys)

          val pushService = new PushService(vapidPublicKey, vapidPrivateKey, "livesdodia.com.br")
          val payload = Json.obj(
            "title" -> s"${evento.horarioFmt} - ${evento.nome}",
            "options" -> Json.obj(
              "body" -> "Sua live acaba de começar",
              "renotify" -> true,
              "tag" -> evento.nome,
              "requireInteraction" -> true,
              "badge" -> "https://livesdodia.com.br/assets/images/logo-180x180.png",
              "icon" -> "https://livesdodia.com.br/assets/images/logo-180x180.png",
              "image" -> evento.linkImagem.getOrElse("https://livesdodia.com.br/assets/images/logo-180x180.png").toString,
              "timestamp" -> evento.data.atZone(ZoneId.systemDefault()).toEpochSecond,
            )
          )
          val notification = new Notification(subscription, payload.toString)
          pushService.send(notification)
        })
      }
    }
  }
}
