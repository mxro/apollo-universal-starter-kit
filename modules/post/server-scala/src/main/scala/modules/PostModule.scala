package modules

import com.byteslounge.slickrepo.repository.Repository
import com.google.inject.Provides
import graphql.resolvers.{PostResolver, PostResolverImpl}
import model.{Comment, Post}
import net.codingwell.scalaguice.ScalaModule
import repositories._
import slick.jdbc.JdbcProfile

class PostModule extends ScalaModule {

  override def configure() {
    bind[PostResolver].to[PostResolverImpl]
  }

  @Provides
  def postRepository(driver: JdbcProfile): Repository[Post, Int] = new PostRepository(driver)
  @Provides
  def commentRepository(driver: JdbcProfile): Repository[Comment, Int] = new CommentRepository(driver)
}