package graphql.resolvers

import akka.actor.{Actor, ActorLogging}
import akka.pattern._
import com.github.jurajburian.mailer.Message
import com.google.inject.Inject
import common.ActorNamed
import common.config.AppConfig
import common.errors.{AlreadyExists, NotFound}
import config.{AuthConfig, MailConfig}
import errors.Unauthenticated
import model._
import models.MailPayload
import org.mindrot.jbcrypt.BCrypt
import repositories.UserRepository
import services.{MailService, MessageTemplateService}
import common.implicits.RichDBIO._
import common.implicits.RichFuture._
import common.implicits.RichTry._
import modules.jwt.model.{JwtContent, Tokens}
import modules.jwt.service.JwtAuthService

import scala.concurrent.{ExecutionContext, Future}

object AuthenticationResolver extends ActorNamed {
  final val name = "AuthenticationResolver"
}

class AuthenticationResolver @Inject()(userRepository: UserRepository,
                                       jwtAuthService: JwtAuthService[JwtContent],
                                       mailService: MailService[Message, MailPayload],
                                       messageTemplateService: MessageTemplateService,
                                       mailConfig: MailConfig,
                                       authConfig: AuthConfig,
                                       appConfig: AppConfig)
                                      (implicit executionContext: ExecutionContext) extends Actor
  with ActorLogging {

  override def receive: Receive = {
    case (input: RegisterUserInput, skipConfirmation: Boolean) => {
      for {
        createdUser <- userRepository.save(
          User(
            username = input.username,
            email = input.email,
            role = "user",
            isActive = skipConfirmation,
            password = BCrypt.hashpw(input.password, BCrypt.gensalt)
          )).run
        mailingResult <- if (!skipConfirmation) {
          mailService.send(
            messageTemplateService.createConfirmRegistrationMessage(
              createdUser,
              appConfig.name,
              mailConfig.address,
              appConfig.url + authConfig.confirmRegistrationRoute + jwtAuthService.createAccessToken(JwtContent(createdUser.id.get)))
          )
        } else Future.successful(MailPayload())
      } yield UserPayload(Some(createdUser), mailingResult.errors)
    }.pipeTo(sender)

    case input: ConfirmRegistrationInput => {
      for {
        tokenContent <- jwtAuthService.decodeContent(input.token).asFuture
        user <- userRepository.findOne(tokenContent.id).run failOnNone NotFound(s"User with id: [${tokenContent.id}] not found.")
        activeUser <- if (!user.isActive) userRepository.update(user.copy(isActive = true)).run else Future.failed(AlreadyExists(s"User with id: [${user.id}] is active"))
        accessToken = jwtAuthService.createAccessToken(JwtContent(activeUser.id.get))
        refreshToken = jwtAuthService.createRefreshToken(JwtContent(activeUser.id.get), user.password)
      } yield AuthPayload(Some(activeUser), Some(Tokens(accessToken, refreshToken)))
    }.pipeTo(sender)

    case input: ResendConfirmationMessageInput => {
      for {
        user <- userRepository.findOne(input.usernameOrEmail).run failOnNone NotFound(s"User with username or email: [${input.usernameOrEmail}] not found.")
        _ <- if (!user.isActive) Future.successful() else Future.failed(AlreadyExists(s"User with id: [${user.id}] is active"))
        _ <- if (BCrypt.checkpw(input.password, user.password)) Future.successful() else Future.failed(Unauthenticated())
        accessToken = jwtAuthService.createAccessToken(JwtContent(user.id.get))
        mailingResult <- mailService.send(
          messageTemplateService.createConfirmRegistrationMessage(
            user,
            appConfig.name,
            mailConfig.address,
            appConfig.url + authConfig.confirmRegistrationRoute + accessToken)
        )
      } yield UserPayload(Some(user), mailingResult.errors)
    }.pipeTo(sender)

    case input: LoginUserInput => {
      for {
        user <- userRepository.findOne(input.usernameOrEmail).run failOnNone NotFound(s"User with username or email: [${input.usernameOrEmail}] not found.")
        _ <- if (BCrypt.checkpw(input.password, user.password)) Future.successful() else Future.failed(Unauthenticated())
        accessToken = jwtAuthService.createAccessToken(JwtContent(user.id.get))
        refreshToken = jwtAuthService.createRefreshToken(JwtContent(user.id.get), user.password)
      } yield AuthPayload(Some(user), Some(Tokens(accessToken, refreshToken)))
    }.pipeTo(sender)

    case input: ForgotPasswordInput => {
      for {
        user <- userRepository.findOne(input.usernameOrEmail).run failOnNone NotFound(s"User with username or email: [${input.usernameOrEmail}] not found.")
        token = jwtAuthService.createAccessToken(JwtContent(user.id.get))
        _ <- mailService.send(
          messageTemplateService.createRecoverPasswordMessage(
            user,
            appConfig.name,
            mailConfig.address,
            appConfig.url + authConfig.confirmRegistrationRoute + token)
        )
      } yield token
    }.pipeTo(sender)

    case input: ResetPasswordInput => {
      for {
        tokenContent <- jwtAuthService.decodeAccessToken(input.token).asFuture
        user <- userRepository.findOne(tokenContent.id).run failOnNone NotFound(s"User with id: [${tokenContent.id}] not found.")
        _ <- userRepository.update(user.copy(password = BCrypt.hashpw(input.password, BCrypt.gensalt))).run
      } yield ResetPayload()
    }.pipeTo(sender)

    case unknownMessage@_ => log.warning(s"Received unknown message: $unknownMessage")
  }
}