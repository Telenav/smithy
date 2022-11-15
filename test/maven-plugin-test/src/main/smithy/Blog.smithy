$version: "2"

namespace sample.blog

use com.telenav.smithy#identity
use com.telenav.smithy#builder
use com.telenav.smithy#samples

service BlogService {
    version : "1.0"
    resources : [Blogs]
}

resource Blogs {
    identifiers: { id: BlogId }
    properties: {
                  title: Title,
                  metadata: BlogMetadata,
                  published: Boolean,
                  body : String
                }
    read: ReadBlog
    list: ListBlogs
    resources: [Comments]
}

resource Comments {
    identifiers: { id: BlogId, commentId : String }
    list: ReadComments
}

@readonly
operation ReadBlog {
    input: BlogSelector
    output: Blog
}

@readonly
operation ListBlogs {
    input: BlogListInput
    output : BlogsOutput
}

@readonly
operation ReadComments {
    input: ListCommentsInput
    output: CommentsOutput
}

/// Input to specify what blog to return
@input
structure BlogSelector {
    /// The blog id
    @required
    id : BlogId
}

/// Input to specify what blog to list comments for
@input
@references([{ resource: Blogs }])
structure ListCommentsInput {
    /// The blog id
    @required
    id : BlogId
}

/// Identifier of a blog
@length(min: 4, max: 256)
@pattern("^[^ \n\r.'\"/\\:,.?;\\[\\]}{]+$")
@samples(
    valid: ["some_thing", "this_is_a_blog", "xxxx"],
    invalid:["This Is A Blog", "I'm a thing", "http://foo", "", "x", "yy", "zzz"])
string BlogId

/// Title of a blog, with some constraints
@length(min: 4, max: 480)
@pattern("^\\S.*\\S$")
string Title

@length(min: 5, max: 4096)
@pattern("^\\S.*\\S$")
string Synopsis


/// Ad-hoc tags that can be applied to a blog
@uniqueItems
list Tags {
    member: String
}

/// A list of Blogs
list BlogList {
    member: BlogHeading
}

/// Output containing a list of blogs
@output
structure BlogsOutput {
    blogs : BlogList
}

/// Output containing comments and identifying the blog they belong to
@output
structure CommentsOutput {
    /// Identifies the blog
    @required
    id : BlogId
    /// The comments
    @required
    comments : CommentsList
}

/// Query for listing blogs
@input
structure BlogListInput {
    since : Timestamp
    tags: Tags
    search: String
}

/// A list of comments
list CommentsList {
    /// The comment
    member: Comment
}

/// Shared properties of a blog entry, sans the body.
@mixin
structure BlogProperties {
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
@references([{resource: Blogs}])
structure BlogHeading with [BlogProperties] {
}

/// A complete blog entry
@references([{resource: Blogs}])
@builder
structure Blog with [BlogProperties] {

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

/// A comment on a blog entry
@builder
structure Comment {

    /// A unique identifier for the comment
    @required
    commentId : String

    /// A title for the comment
    title: Title

    /// The body of the comment
    @required
    commentBody: String

    /// The creation timestamp
    @required
    @identity
    created: Timestamp

    /// The author of the comment
    @required
    @identity
    author: String

    /// The email address of the comment author
    authorEmail: Email

    approved: Boolean = false
}
