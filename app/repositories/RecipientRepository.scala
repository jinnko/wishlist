package repositories

import anorm._
// import anorm.JodaParameterMetaData._
import anorm.SqlParser._
import com.google.inject.ImplementedBy
import java.sql.Connection
import javax.inject.{Inject, Singleton}
// import org.joda.time.DateTime
import play.api.db._
// import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models._
import controllers.WithLogging



@ImplementedBy(classOf[DefaultRecipientLookup])
trait RecipientLookup {

   def recipientRepository: RecipientRepository

   def findRecipient(username: String) = recipientRepository.findRecipient(username)

   def findVerification(username: String): Future[Option[String]] = Future.successful(None)

}

@Singleton
class DefaultRecipientLookup @Inject() (val recipientRepository: RecipientRepository)
extends RecipientLookup


@ImplementedBy(classOf[DefaultRecipientRepository])
trait RecipientRepository extends Repository with WithLogging {

   val MapToRecipient =
      get[Long]("recipientid") ~
      get[String]("username") ~
      get[Option[String]]("fullname") ~
      get[String]("email") ~
      get[Boolean]("isAdmin") map {
      case recipientId~username~fullname~email~isadmin =>
         Recipient( Some(recipientId), username, fullname, email, password = None, isadmin)
      }


   def findRecipient(username: String): Future[Option[Recipient]] =
      Future {
         db.withConnection { implicit connection =>
            logger.debug(s"Looking up recipient: ${username}")
            SQL"""
                  select *
                  from recipient
                  where username = ${username.trim.toLowerCase()}
               """
               .as(MapToRecipient.singleOpt)
         }
      }

   def findRecipientById(recipientId: Long) =
      Future {
         db.withConnection { implicit connection =>
            SQL"""
                  select *
                  from recipient
                  where recipientid = $recipientId
               """
                  .as(MapToRecipient.singleOpt)
         }
      }

   def saveRecipient(recipient: Recipient): Future[Either[_,Recipient]] =
      Future {
         db.withConnection{ implicit connection =>
            logger.info(s"Saving new recipient: ${recipient.username}")
            val nextRecipientId = SQL("SELECT NEXTVAL('recipient_seq')").as(scalar[Long].single)
            SQL"""
                  insert into recipient
                     (recipientid,username,fullname,email,password,isadmin)
                  values ($nextRecipientId, ${recipient.username}, ${recipient.fullname},
                          ${recipient.email}, ${recipient.password}, false)
               """
            .executeInsert()
            .map{ recipientId =>
               recipient.copy(recipientId = Some(recipientId))
            }.toRight(new IllegalStateException("Saving recipient failed"))
         }
      }

   def findCredentials(recipient: Recipient): Future[Option[String]] =
      Future {
         db.withConnection{ implicit connection =>
            recipient.recipientId.map{ recipientId =>
               SQL"""
                     select password
                     from recipient
                     where recipientid = $recipientId
                  """
               .as(scalar[String].single)
            }
         }
      }

   def isEmailVerified(recipient: Recipient) : Future[Boolean] =
      Future {
         db.withConnection { implicit connection =>
            recipient.recipientId.fold(false){ recipientId =>
               SQL"""
                     SELECT count(*) = 1 FROM emailverification
                     WHERE recipientid = $recipientId
                     AND verified = true
                  """
               .as(scalar[Boolean].single)
            }
         }
      }


   def findVerificationHash(recipient:Recipient): Future[Option[String]] =
      Future {
         recipient.recipientId.flatMap { recipientId =>
            db.withConnection { implicit connection =>
               SQL"""
                     SELECT verificationhash
                     FROM emailverification
                     WHERE recipientid = $recipientId
                  """
               .as(scalar[String].singleOpt)
            }
         }
      }

   def saveVerificationHash(recipient: Recipient, verificationHash: String): Future[String] =
      Future {
         recipient.recipientId.fold{
            throw new IllegalStateException("No recipient id")
         } { recipientId =>
            db.withConnection { implicit connection =>
               SQL"""
                     INSERT INTO emailverification
                     (recipientid,email,verificationhash)
                     VALUES
                     ($recipientId,${recipient.email},$verificationHash)
                  """
                  .executeInsert()
               verificationHash
            }
         }
      }

   def doesVerificationMatch(recipient: Recipient, verificationHash: String): Future[Boolean] =
      Future {
         recipient.recipientId.fold{
            throw new IllegalStateException("No recipient id")
         } { recipientId =>
            db.withConnection { implicit connection =>
               SQL"""
                 SELECT count(*) = 1
                 FROM emailverification
                 WHERE recipientid = $recipientId
                 AND email = ${recipient.email}
                 AND verificationhash = $verificationHash
               """
               .as(scalar[Boolean].single)
            }
         }
      }


   def setEmailAsVerified(recipient: Recipient, verificationHash: String): Future[Boolean] =
      Future {
         recipient.recipientId.fold{
            throw new IllegalStateException("No recipient id")
         } { recipientId =>
            db.withConnection { implicit connection =>
               SQL"""
                 UPDATE emailverification
                 set verified = true
                 WHERE recipientid = $recipientId
                 and verificationhash = $verificationHash
               """
               .executeUpdate()
            }
         }
      }
      .map{ _ > 0 }



   def findOrganisers(wishlist: Wishlist): Future[List[Recipient]] =
      Future {
         wishlist.wishlistId.fold {
            throw new IllegalArgumentException("No wishlist id")
         } { wishlistId =>
            db.withConnection { implicit connection =>
               SQL"""
                     SELECT rec.*
                     FROM recipient rec
                     INNER JOIN wishlistorganiser wo on wo.recipientid = rec.recipientid
                     where wo.wishlistid = $wishlistId
                     ORDER BY rec.username
                  """
                  .as(MapToRecipient *)
            }
         }
      }
}

@Singleton
class DefaultRecipientRepository @Inject() (val dbApi: DBApi) extends RecipientRepository
