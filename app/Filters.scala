
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.http.DefaultHttpFilters
import play.api.Logger
import play.api.mvc._
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsernameFilter @Inject()(
    implicit override val mat: Materializer,
    exec: ExecutionContext) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      requestHeader.session.get(Security.username).foldLeft(result){ (result, username) =>
         result.withHeaders("X-Username" -> username)
      }
    }
  }

}


class LoggingFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {

   //  Logger.debug(s"Request: [${requestHeader.method}] ${requestHeader.uri} ")

    nextFilter(requestHeader).map { result =>

      val location = result.header.headers.get("Location").map( l => s" >> $l").getOrElse("")
      Logger.debug(s"Response: [${requestHeader.method}] ${requestHeader.uri} => ${result.header.status}${location}")

      result
    }
  }
}


class Filters @Inject() (
      securityHeadersFilter: SecurityHeadersFilter,
      gzipFilter: GzipFilter,
      loggingFilter: LoggingFilter,
      usernameFilter: UsernameFilter
  ) extends DefaultHttpFilters(securityHeadersFilter, gzipFilter, loggingFilter, usernameFilter)
