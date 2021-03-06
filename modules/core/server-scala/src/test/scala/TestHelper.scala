import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import common.graphql.UserContext
import common.routes.graphql.{GraphQLRoute, HttpHandler, WebSocketHandler}
import common.shapes.ServerModule
import core.guice.injection.InjectorProvider
import modules.session.JWTSessionImpl
import monix.execution.Scheduler
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}
import sangria.execution.{Executor, QueryReducer}

trait TestHelper extends WordSpec
  with ScalatestRouteTest
  with BeforeAndAfter
  with BeforeAndAfterAll
  with Matchers
  with MockFactory {

  val endpoint: String = "/graphql"
  lazy implicit val scheduler: Scheduler = inject[Scheduler]

  def inject[T: Manifest]: T = InjectorProvider.inject[T]

  def routesWithGraphQLSchema[T <: ServerModule : Manifest]: Route = {
    val graphQl = new TestGraphQLSchema(inject[T])
    val graphQlExecutor = Executor(
      schema = graphQl.schema,
      queryReducers = List(
        QueryReducer.rejectMaxDepth[UserContext](graphQl.maxQueryDepth),
        QueryReducer.rejectComplexQueries[UserContext](graphQl.maxQueryComplexity, (_, _) => new Exception("maxQueryComplexity"))
      )
    )
    val httpHandler = new HttpHandler(graphQl, graphQlExecutor)
    val webSocketHandler = new WebSocketHandler(graphQl, graphQlExecutor)
    val graphQLRoute = new GraphQLRoute(httpHandler, inject[JWTSessionImpl], webSocketHandler, graphQl)
    graphQLRoute.routes
  }
}