package com.usableapps.pc
import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO, Resource}
import cats.implicits._
import fs2.Stream
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.http4s.Http4sBackend
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{NothingT, Request, Response, SttpBackend}

import scala.concurrent.ExecutionContext

object ExtractFromResource {
	def apply[T](r: Resource[IO, T])(implicit cf: ContextShift[IO]): (T, () => Unit) = {
		val tReady: MVar[IO, T] = MVar.empty[IO, T].unsafeRunSync()
		val done: MVar[IO, Unit] = MVar.empty[IO, Unit].unsafeRunSync()

		// first extracting it from the use method. use will only complete when the `done` mvar is filled, which is done
		// by the returned method
		r.use { _t =>
			tReady.put(_t) >> done.take
		}
			.start
			.unsafeRunSync()

		(tReady.take.unsafeRunSync(), () => done.put(()).unsafeRunSync())
	}
}


class Http4sBackendEx(delegate: SttpBackend[IO, Stream[IO, Byte], NothingT], doClose: () => Unit)
	extends SttpBackend[IO, Stream[IO, Byte], NothingT] {
	override def send[T](request: Request[T, Stream[IO, Byte]]): IO[Response[T]] = delegate.send(request)
	override def openWebsocket[T, WS_RESULT](
												request: Request[T, Stream[IO, Byte]],
												handler: NothingT[WS_RESULT]
											): IO[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)
	override def responseMonad: MonadError[IO] = delegate.responseMonad
	override def close(): IO[Unit] = IO(doClose())
}

object Http4sBackendEx {
	def apply()(implicit cf: ContextShift[IO]): Http4sBackendEx = {
		val blazeClientBuilder = BlazeClientBuilder[IO](ExecutionContext.Implicits.global)
		val (backend, doClose) = ExtractFromResource(Http4sBackend.usingClientBuilder(blazeClientBuilder))
		new Http4sBackendEx(new LoggingSttpBackend[IO, Stream[IO, Byte], sttp.client.NothingT](backend), doClose)
	}
}
