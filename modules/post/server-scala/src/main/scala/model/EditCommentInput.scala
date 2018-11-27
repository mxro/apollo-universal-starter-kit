package model

case class EditCommentInput(id: Int,
                            content: String,
                            postId: Int)

object EditCommentInput {

  implicit def inputToComment(input: EditCommentInput): Comment = {
    Comment(id = input.id,
            content = input.content,
            postId = input.postId
    )
  }
}