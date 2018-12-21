package repositories

import core.slick.SchemaInitializer
import javax.inject.Inject
import model.{Comment, CommentTable}
import model.CommentTable.CommentTable

import scala.concurrent.ExecutionContext

class CommentSchemaInitializer @Inject()(implicit executionContext: ExecutionContext) extends SchemaInitializer[CommentTable] {

  import driver.api._

  override val context = executionContext
  override val name: String = CommentTable.name
  override val table = TableQuery[CommentTable]

  override def seedDatabase(tableQuery: TableQuery[CommentTable]): DBIOAction[_, NoStream, Effect.Write] = {
    val comments = List.range(1, 11).map(num =>
      Comment(id = Some(num),
        content = s" Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim " +
          s"id [$num] est laborum.",
        postId = 1))
    tableQuery ++= comments
  }
}