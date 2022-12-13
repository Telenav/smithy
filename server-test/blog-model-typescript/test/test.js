var Comment = require('../target/model/BlogServiceModel').Comment;
var CommentId = require('../target/model/BlogServiceModel').CommentId;
var Email = require('../target/model/BlogServiceModel').Email;
var Title = require('../target/model/BlogServiceModel').Title;
var ListBlogsInput = require('../target/model/BlogServiceModel').ListBlogsInput;
var ReadBlogInput = require('../target/model/BlogServiceModel').ReadBlogInput;
var BlogId = require('../target/model/BlogServiceModel').BlogId;

var listBlogs = require('../target/blog_client_proto').listBlogs;
var listComments = require('../target/blog_client_proto').listComments;
var readBlog = require('../target/blog_client_proto').readBlog;

//import * as Stuff from '../target/BlogService.js';

console.log('Hello world', Email);

// constructor(author, commentBody, commentId, created, approved, authorEmail, title) {
let e = new Email("foo@bar.com");
let d = new Date(Date.parse('2013-07-16T19:23Z'));
let c = new Comment("Me", "This is a comment", new CommentId(12345), d, true, e, new Title("This is stuff"));

console.log("JSON: ")
console.log(c.toJSON());

console.log("RAW:\n" + JSON.stringify(c));


let c1 = JSON.parse(c.toJsonString())

console.log("C! ", c1);

let c2 = Comment.fromJson(c.toJsonString());

console.log(c2.toJSON());

console.log("And raw:\n" + JSON.stringify(c2))


let prom = listBlogs(new ListBlogsInput(), 'localhost:8123', 'http');

prom.then((what) => {
    console.log("GOT ", what);
}).catch((err) => {
    console.log("Error", err);
    if (typeof err.responseText === 'function') {
        console.log("GOT: " + err.responseText());
    }
});

let prom2 = readBlog(new ReadBlogInput( new BlogId('maven')), 'localhost:8123', 'http');

prom2.then((what) => {
    console.log("GOT ", what);
}).catch((err) => {
    console.log("Error", err);
    if (typeof err.responseText === 'function') {
        console.log("GOT: " + err.responseText());
    }
});

