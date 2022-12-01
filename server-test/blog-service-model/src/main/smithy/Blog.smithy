$version: "2"

namespace com.telenav.blog

use com.telenav.smithy#identity
use com.telenav.smithy#builder
use com.telenav.smithy#samples
use com.telenav.smithy#authenticated
use com.telenav.smithy#genericRestProtocol

/// A service for blogs
@genericRestProtocol
@cors(additionalAllowedHeaders:["x-telenav-req-id"], additionalExposedHeaders: ["x-telenav-req-id"])
service BlogService {
    version : "1.0"
    resources : [Blogs]
}

/// Resource providing blog entries
resource Blogs {
    identifiers: { id: BlogId }
    properties: {
                  title: Title,
                  metadata: BlogMetadata,
                  published: Boolean,
                  synopsis: Synopsis,
                  tags: Tags,
                  body : String
                }
    read: ReadBlog
    list: ListBlogs
    create: NewBlog
    resources: [Comments]
}

/// Resource providing comments.
resource Comments {
    identifiers: { id: BlogId, commentId : CommentId }
    list: ListComments
    create: PutComment
    operations: [ApproveComment]
}

/// Approve (or un-approve) a comment, making it visible or invisible to non-admin users.
@idempotent
@http(method:"POST", uri:"/blog/{id}/{commentId}", code: 201)
@authenticated(mechanism: "basic", payload : "com.telenav.blog#AuthUser")
operation ApproveComment {
    /// The blog and comment IDs
    input : ApproveCommentInput
    /// Whether or not the approved state of the comment
    /// was changed by applying this operation
    output: ApproveCommentOutput
}

/// Input to approving a comment, consisting of the blog id,
/// comment id, and whether or not to approve it.
@input
structure ApproveCommentInput {
    /// The identifier of the blog entry on which the comment is
    /// to be approved.
    @required
    @httpLabel
    id : BlogId,
    /// The identifier of the comment to be approved or de-approved.
    @required
    @httpLabel
    commentId : CommentId,
    /// The desired approved state for the comment.
    @required
    @httpPayload
    approved : ApproveCommentState
}

/// Wrapper type for the desired approval state of a comment, so it
/// can be used as an HTTP payload
structure ApproveCommentState {
    approved : Boolean = true
}

/// Result of changing the approval state of a comment - this
/// should indicate whether the approval state of the comment *changed*
/// as a consequence of an apporval operation
@output
structure ApproveCommentOutput {
    /// Whether or not the operation changed the approval state
    @required
    stateChanged : Boolean
    /// The approval state at the time of responding
    @required
    approved : Boolean
}

/// Read blogs.
@readonly
@http(method:"GET", uri:"/blog/{id}", code:200)
operation ReadBlog {
    input: ReadBlogInput
    output: ReadBlogOutput
}

/// Lists blogs.  Authenticated so an admin can request unpublished blogs.
@readonly
@http(method:"GET", uri:"/blog?search=search&tags=tags&since=since&count=count", code:200)
@authenticated(mechanism: "basic", payload : "com.telenav.blog#AuthUser", optional : true)
operation ListBlogs {
    input: ListBlogsInput
    output : ListBlogsOutput
}

/// Input to a request to list comments for a blog entry .Authenticated so an admin can request unpublished comments and blogs.
@readonly
@http(method:"GET", uri:"/blog/{id}/comments", code: 200)
@authenticated(mechanism: "basic", payload : "com.telenav.blog#AuthUser", optional : true)
operation ListComments {
    input: ListCommentsInput
    output: ListCommentsOutput
}

/// Add a new comment to a blog.
@idempotent
@http(method:"PUT", uri:"/blog/{id}/comments", code: 201)
operation PutComment {
    input: PutCommentInput
    output: PutCommentOutput
}

/// Create new new blog entry.
//@idempotent
@http(method:"PUT", uri:"/blog", code: 201)
@authenticated(mechanism : "basic", payload : "com.telenav.blog#AuthUser")
operation NewBlog {
    input: NewBlogInput
    output: NewBlogOutput
}

/// An authorized user
@sensitive
@samples(
    valid: ["me", "you", "them", "joe", "mom"],
    invalid:["", "x", "y", "1"]
)
structure AuthUser {
    /// A user name
    @required
    @length(min:1, max:32)
    name : String
}

/// Response to creating a new blog entry
@output
@references([{resource: Blogs}])
structure NewBlogOutput {
    @required
    id : BlogId
}

@output
//@references([{resource: Comments}])
structure PutCommentOutput {
    @required
    id : String
}

@input
@references([{resource: Blogs}])
structure PutCommentInput {
    @httpLabel
    @required
    id : BlogId

    @required
    @httpPayload
    content: InboundComment
}

structure InboundComment with [CommentContent] {

}

/// Input to specify what blog to return
@input
structure ReadBlogInput {
    /// The blog id
    @required
    @httpLabel
    id : BlogId
}

/// Input to specify what blog to list comments for
@input
@references([{ resource: Blogs }])
structure ListCommentsInput {
    /// The blog id
    @required
    @httpLabel
    id : BlogId

    @httpQuery("approved")
    approved: Boolean = false
}

/// Identifier of a blog
@length(min: 2, max: 256)
@pattern("^[^ .'\"/\\:,.?;\\[\\]}{]+$")
@samples(
    valid: ["some_thing", "this_is_a_blog", "xxxx"],
    invalid:["This Is A Blog", "I'm a thing", "http://foo", "", "x", "y", "1"])
string BlogId

/// Title of a blog, with some constraints
@length(min: 2, max: 480)
string Title

@length(min: 5, max: 4096)
string Synopsis


/// Ad-hoc tags that can be applied to a blog
@uniqueItems
list Tags {
    member: String
}

/// A list of Blogs
list BlogList {
    member: BlogInfo
}

/// Output containing a list of blogs
@output
structure ListBlogsOutput {
    /// The list of blogs
    @required
    blogs : BlogList
}

/// Output containing comments and identifying the blog they belong to
@output
structure ListCommentsOutput {
    /// Identifies the blog
    @required
    id : BlogId
    /// The comments
    @required
    comments : CommentsList
}

@mixin
structure HttpCacheHeaders {
    @httpHeader("if-none-match")
    etag: String

    @httpHeader("if-modified-since")
    ifModifiedSince: Timestamp
}

/// Query for listing blogs
@input
@builder("debug")
structure ListBlogsInput with [HttpCacheHeaders] {
    @httpQuery("since")
    since : Timestamp

    @httpQuery("tags")
    tags: Tags

    @httpQuery("search")
    search: String

    @httpQuery("max")
    @range(min: 1)
    count: Short

}

/// A list of comments
list CommentsList {
    /// The comment
    member: Comment
}

@input
structure NewBlogInput {
    @required
    title : Title

    @required
    body : String

    synopsis : Synopsis

    tags: Tags

    published: Boolean = false
}

/// Shared properties of a blog entry, sans the body.
@mixin
structure PersistedBlogProperties {
    /// Identifies the blog entry
    @identity
    @required
    id : BlogId

    /// The title of the blog
    @required
    title : Title

    /// Metadata associated with a blog entry -
    /// creation and modification dates, tags.
    @required
    metadata : BlogMetadata

    @required
    published : Boolean
}

@builder
structure BlogMetadata {
    /// The creation time of the blog entry
    @required
    created: Timestamp

    synopsis: Synopsis

    /// The last modified time of the blog
    lastModified: Timestamp

    /// Any tags that apply to the blog
    tags: Tags
}

/// A blog, sans its body
@builder
@references([{resource: Blogs}])
structure BlogInfo with [PersistedBlogProperties] {
}

/// A complete blog entry
@references([{resource: Blogs}])
@builder
structure ReadBlogOutput with [PersistedBlogProperties] {

    /// The textual body of the blog
    @required
    @length(min:128)
    body : String

}

/// An email address, marked sensitive so it may be elided in logs
@sensitive
@length(min: 5, max: 512)
@pattern("^\\S.*?@[^ @.]+\\.[^ @]*$")
@samples(
    valid: ["foo@bar.com", "bubu@patodlehodt.blarg.moof.org", "x@y.com",
            "snorks@portlebortsnorks.com", "Snorks Portleblort <snorks@portlebortsnorks.com>"]
    invalid: ["", "x", "x@", "xxxxxxxxx@", "@", "@yodle", "foo@bar.com@moo",
              "foo@bar.com and stuff", "@bar.com", "@bar.blee.com", "A Big Thing",
              ".......", "....@....", "snorks@.com"]
)
string Email

@mixin
structure CommentContent {
    /// A title for the comment
    title: Title

    /// The body of the comment
    @required
    @length(min: 5, max: 16384)
    commentBody: String

    /// The author of the comment
    @required
    @identity
    @length(min: 1, max: 64)
    author: String

    /// The email address of the comment author
    authorEmail: Email
}

@length(min: 12, max: 16)
@pattern("^[a-zA-Z0-9_-]{12,16}$")
@samples(valid: ["hey-you-I-am-1", "IlikeCheese1111", "123456789abcdef"], invalid: [
    "this", "that", "", "wug", "bunch of words", "you,me,he,she,tree,whee"])
string CommentId

/// A comment on a blog entry
@builder
structure Comment with [CommentContent]{

    /// A unique identifier for the comment
    @required
    commentId : CommentId

    /// The creation timestamp
    @required
    @identity
    created: Timestamp

    approved: Boolean = true
}
