// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.effect._
import skunk.net.Protocol
import skunk.Session
import skunk.util.Namer
import skunk.util.Typer.Strategy
import ffstest.FTest
import natchez.Trace.Implicits.noop
import skunk.net.BufferedMessageSocket
import skunk.net.message.BackendMessage
import skunk.net.message.FrontendMessage
import skunk.util.Origin
import fs2.concurrent.Signal
import skunk.data.TransactionStatus
import cats.effect.concurrent.Deferred
import skunk.data.Notification
import skunk.net.message.BackendKeyData
import skunk.net.MessageSocket

trait SimTest extends FTest with SimMessageSocket.DSL {

  private class SimulatedBufferedMessageSocket(ms: MessageSocket[IO]) extends BufferedMessageSocket[IO] {
    def receive: IO[BackendMessage] = ms.receive
    def send(message: FrontendMessage): IO[Unit] = ms.send(message)
    def history(max: Int): IO[List[Either[Any,Any]]] = ms.history(max)
    def expect[B](f: PartialFunction[BackendMessage,B])(implicit or: Origin): IO[B] = ms.expect(f)
    def flatExpect[B](f: PartialFunction[BackendMessage,IO[B]])(implicit or: Origin): IO[B] = ms.flatExpect(f)
    def transactionStatus: Signal[IO,TransactionStatus] = ???
    def parameters: Signal[IO,Map[String,String]] = ???
    def backendKeyData: Deferred[IO,BackendKeyData] = ???
    def notifications(maxQueued: Int): fs2.Stream[IO, Notification[String]] = ???
    def terminate: IO[Unit] = ???
  }

  def simSession(sim: Simulator, user: String, database: String, password: Option[String] = None): IO[Session[IO]] =
    for {
      bms <- SimMessageSocket(sim).map(new SimulatedBufferedMessageSocket(_))
      nam <- Namer[IO]
      pro <- Protocol.fromMessageSocket(bms, nam)
      _   <- pro.startup(user, database, password)
      ses <- Session.fromProtocol(pro, nam, Strategy.BuiltinsOnly)
    } yield ses

  def simTest[A](name: String, sim: Simulator, user: String = "Bob", database: String = "db", password: Option[String] = None)(f: Session[IO] => IO[A]): Unit =
    test(name)(simSession(sim, user, database, password).flatMap(f))

}
