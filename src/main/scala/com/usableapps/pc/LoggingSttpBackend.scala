package com.usableapps.pc

import cats.effect.IO
import sttp.client.{Request, Response, SttpBackend}
import com.typesafe.scalalogging.StrictLogging
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse

class LoggingSttpBackend[IO[_], S, WS_HANDLER[_]](delegate: SttpBackend[IO, S, WS_HANDLER])
	extends SttpBackend[IO, S, WS_HANDLER]
		with StrictLogging {

	override def send[T](request: Request[T, S]): IO[Response[T]] = {
		responseMonad.map(responseMonad.handleError(delegate.send(request)) {
			case e: Exception =>
				logger.error(s"Exception when sending request: $request", e)
				responseMonad.error(e)
		}) { response =>
			if (response.isSuccess) {
				logger.debug(s"For request: $request got response: $response")
			} else {
				logger.warn(s"For request: $request got response: $response")
			}
			response
		}
	}
	override def openWebsocket[T, WS_RESULT](
												request: Request[T, S],
												handler: WS_HANDLER[WS_RESULT]
											): IO[WebSocketResponse[WS_RESULT]] = {
		responseMonad.map(responseMonad.handleError(delegate.openWebsocket(request, handler)) {
			case e: Exception =>
				logger.error(s"Exception when opening a websocket request: $request", e)
				responseMonad.error(e)
		}) { response =>
			logger.debug(s"For ws request: $request got headers: ${response.headers}")
			response
		}
	}
	override def close(): IO[Unit] = delegate.close()
	override def responseMonad: MonadError[IO] = delegate.responseMonad
}
