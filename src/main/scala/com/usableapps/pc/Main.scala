package com.usableapps.pc

import java.io.{File, FileOutputStream}

import cats.effect.concurrent.MVar
import cats.effect._
import com.typesafe.config.ConfigRenderOptions
import fs2.Stream
import io.circe.Json
import io.circe.parser.parse
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client._
import sttp.client.http4s.Http4sBackend
import pureconfig.module.catseffect._
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.model.CookieWithMeta
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

case class Config(username: String, password: String, csrf : String = "", cookieJar: Map[String, CookieWithMeta] = Map.empty)

object Main extends IOApp {
	override def run(args: List[String]): IO[ExitCode] = {

		implicit val backend: Http4sBackendEx = Http4sBackendEx()

		import PersonalCapital._

		import io.circe.optics.JsonPath._

		val _accounts = root.spData.accounts.each.currentBalance.bigDecimal

		def outputStream(f: File): IO[FileOutputStream] = IO(new FileOutputStream(f))

		for {
			config <- loadConfigF[IO, Config]
			session <- getSession(config).login()
			ostream <- outputStream(new File(this.asInstanceOf[Any].getClass.getResource("/application.conf").getPath))
			(resp, session) <- session.fetch("/newaccount/getAccounts")
			exitCode <- IO(ExitCode.Success)
			balances = _accounts.getAll(parse(resp).getOrElse(Json.Null))
//			_ <- IO(println(resp))
//			_ <- IO(println(balances))
//			_ <- IO(println(session.config))
			_ <- saveConfigToStreamF[IO, Config](session.config, ostream, ConfigRenderOptions.defaults().setJson(false))
		} yield exitCode

	}
}

