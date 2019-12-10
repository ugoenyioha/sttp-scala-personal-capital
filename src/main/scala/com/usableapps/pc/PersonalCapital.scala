package com.usableapps.pc
import cats.effect._
import io.circe.optics.JsonPath.root
import org.http4s.{Header, UrlForm}
import org.http4s.client.dsl.Http4sClientDsl
import sttp.client._
import sttp.client.http4s._
import sttp.model.Uri.PathSegment
import sttp.model.{CookieWithMeta, Uri}
import sun.rmi.transport.Endpoint

import scala.collection.immutable
import scala.util.matching.Regex

sealed trait VerificationMode
case object VerificationModeEmail extends VerificationMode
case object VerificationModeSMS extends VerificationMode

case class PersonalCapitalSession(config: Config, cookieJar: Map[String, CookieWithMeta] = Map.empty, implicit val backendEx: Http4sBackendEx)  extends Http4sClientDsl[IO] {

	private val csrf_regexp: Regex = "globals.csrf='([a-f0-9-]+)'".r
	private val base_url = "home.personalcapital.com"
	private val api_endpoint = List("api")

	private def get_csrf_from_homepage: IO[(String, PersonalCapitalSession)] = {
		val req = basicRequest.get(uri"https://home.personalcapital.com/page/login/goHome")
    		.cookies(cookieJar.values)

		for {
			resp <- req.send()
		} yield {
			val newResp = adjustResponse(resp)
			val newCookies  = newResp.cookies.map(x => x.name -> x).toMap
			val cookiesToAssign = if (config.cookieJar.nonEmpty) config.cookieJar else newCookies

			newResp
				.body
				.fold(
					(error: String) =>
						sys.error(error),
					(body: String) => {
						val csrf_value = csrf_regexp.findFirstMatchIn(body).fold("")(x => x.group(1))
						(csrf_value, this.copy(config.copy(csrf = csrf_value, cookieJar = cookiesToAssign), cookiesToAssign, backendEx))
					})
		}
	}

	private def adjustResponse(resp: Response[Either[String, String]]) = {
		val new_headers = resp.headers collect {
			case sttp.model.Header(name, value) =>
				if (name == "Set-Cookie")
					new sttp.model.Header(name, value.replaceAll("""-""", " ").replace("Max Age", "Max-Age"))
				else
					new sttp.model.Header(name, value)
		}

		resp.copy(headers = new_headers)
	}

	private def postJson(endpoint: String, form: Map[String, String]): IO[(String, PersonalCapitalSession)] = {
		val req = basicRequest.post(Uri.unsafeApply("https", base_url, api_endpoint ++ endpoint.split("""/""")))
    				.cookies(cookieJar.values)
					.cookies(cookieJar.values).body(form)

		for {
			resp <- req.send()
		} yield {
			val newResp = adjustResponse(resp)
			val cookies  = newResp.cookies.map(x => x.name -> x).toMap

			newResp
				.body
    			.fold(
					(error: String) =>
						{	println(error)
							sys.error(error)
						},
					(body: String) => {
						println(body)
						(body, this.copy(config.copy(cookieJar = cookieJar ++ cookies), cookieJar = cookieJar ++ cookies))
					}
				)
		}
	}

	private def fetchJson(endpoint: String, data: Map[String, String] = Map.empty) = {
		val base = Map(
			"lastServerChangeId" -> "-1",
			"csrf" -> this.config.csrf,
			"apiClient" -> "WEB"
		)

		postJson(endpoint, base ++ data)
	}


	private def identify_user(session: PersonalCapitalSession, username: String): IO[(String, PersonalCapitalSession)] = {
		val data = Map(
			"username" -> username, //
			"csrf" -> session.config.csrf, //
			"apiClient" -> "WEB", //
			"bindDevice" -> "false", //
			"skipLinkAccount" -> "false", //
			"referrerId" -> "", //
			"redirectTo" -> "", //
			"skipFirstUse" -> "" //
		)

		val _new_csrf = root.spHeader.csrf.string
		val _auth_level = root.spHeader.authLevel.string

		import io.circe._
		import io.circe.parser._

		for {
			(resp, session) <- postJson("login/identifyUser", data)
			new_csrf = _new_csrf.getOption(parse(resp).getOrElse(Json.Null))
			auth_level = _auth_level.getOption(parse(resp).getOrElse(Json.Null))
		} yield (auth_level.getOrElse(""), session.copy(config = session.config.copy(csrf = new_csrf.getOrElse(""))))
	}

	private def two_factor_challenge(session: PersonalCapitalSession, mode: VerificationMode) = {
		mode match {
			case VerificationModeSMS =>
				challenge_sms(session)
			case VerificationModeEmail =>
				challenge_email(session)
		}
	}

	private def two_factor_authenticate(session: PersonalCapitalSession, mode: VerificationMode, code: String) = {
		mode match {
			case VerificationModeSMS =>
				authenticate_sms(code, session)
			case VerificationModeEmail =>
				authenticate_email(code, session)
		}
	}

	private def generate_challenge_payload(challenge_type: String, csrf: String) = {
		Map(
			"challengeReason" -> "DEVICE_AUTH",
			"challengeMethod" -> "OP",
			"challengeType" -> challenge_type,
			"apiClient" -> "WEB",
			"bindDevice" -> "false",
			"csrf" -> csrf
		)
	}

	private def generate_authentication_payload(code: String, csrf: String) = {
		Map(
			"challengeReason" -> "DEVICE_AUTH",
			"challengeMethod" -> "OP",
			"apiClient" -> "WEB",
			"bindDevice" -> "false",
			"code" -> code,
			"csrf" -> csrf
		)
	}

	private def challenge_email(session: PersonalCapitalSession) = {
		val payload = generate_challenge_payload("challengeEmail", session.config.csrf)
		session.postJson("credential/challengeEmail", payload)
	}

	private def authenticate_email(code: String, session: PersonalCapitalSession) = {
		val json = generate_authentication_payload(code, session.config.csrf)
		session.postJson("credential/authenticateEmailByCode", json)
	}

	private def challenge_sms(session: PersonalCapitalSession) = {
		val json = generate_challenge_payload("challengeSMS", session.config.csrf)
		session.postJson("credential/challengeSms", json)
	}
	private def authenticate_sms(code: String, session: PersonalCapitalSession) = {
		val json = generate_authentication_payload(code, session.config.csrf)
		session.postJson("credential/authenticateSms", json)
	}

	private def authenticate_password(session: PersonalCapitalSession, password: String) = {

		val data = Map(
			"bindDevice" -> "true",
			"deviceName" -> "ugolaptop",
			"redirectTo" -> "",
			"skipFirstUse" -> "",
			"skipLinkAccount" -> "false",
			"referrerId" -> "",
			"passwd" -> password,
			"apiClient" -> "WEB",
			"csrf" -> session.config.csrf
		)

		session.postJson("/credential/authenticatePassword", data)
	}

	private def putStrlLn(value: String): IO[Unit] = IO(println(value))
	private val readLn: IO[String] = IO(scala.io.StdIn.readLine)

	def login() : IO[PersonalCapitalSession] =
		for {
			(resp, session) <- get_csrf_from_homepage
			(auth_level, session) <- session.identify_user(session, config.username)
			_ <- if (!(auth_level == "USER_REMEMBERED")) {
				for {
					(_, session) <- session.two_factor_challenge(session, VerificationModeEmail)
					_ <- putStrlLn("code: ")
					code <- readLn
					(_, session) <- session.two_factor_authenticate(session, VerificationModeEmail, code)
				} yield ()
			} else {
				IO.unit
			}
			(_, session) <- session.authenticate_password(session, config.password)
		} yield session

	def fetch(endpoint: String,
			  data: Map[String, String] = Map.empty): IO[(String, PersonalCapitalSession)]
	= fetchJson(endpoint, data)
}

object PersonalCapital {
	def getSession(config: Config,
				   cookieJar: Map[String, CookieWithMeta] = Map.empty)
				  (implicit backendEx: Http4sBackendEx): PersonalCapitalSession =
		new PersonalCapitalSession(config, cookieJar, backendEx)
}
