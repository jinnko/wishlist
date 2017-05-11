package com.flurdy.wishlist

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{Helpers, TestServer}
import play.api.libs.ws.WSClient
import org.scalatest.{BeforeAndAfterAll, Suite}

trait WithTestServer {

   var app: Application = _
   private var server: TestServer = _
   val port = Helpers.testServerPort
   lazy val baseUrl: String = s"http://localhost:$port"

   def startServer() = {
      app = new  GuiceApplicationBuilder().build()
      server = new TestServer(port, app)
      server.start()
   }

   def stopServer() = server.stop()

   def getWsClient() = app.injector.instanceOf[WSClient]

}

trait StartAndStopServer extends Suite with BeforeAndAfterAll with WithTestServer {

   override def beforeAll = startServer()

   override def afterAll = stopServer()

}
