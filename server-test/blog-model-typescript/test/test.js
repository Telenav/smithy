var Comment = require('../target/BlogService.js').Comment;
var CommentId = require('../target/BlogService.js').CommentId;
var Email = require('../target/BlogService.js').Email;
var Title = require('../target/BlogService.js').Title;

//import * as Stuff from '../target/BlogService.js';

console.log('Hello world', Email);

// constructor(author, commentBody, commentId, created, approved, authorEmail, title) {
let e = new Email("foo@bar.com");
let d = new Date(Date.parse('2013-07-16T19:23Z'));
let c = new Comment("Me", "This is a comment", new CommentId(12345), d, true, e, new Title("This is stuff"));

console.log("JSON: ")
console.log(c.toJson());

console.log("RAW", JSON.stringify(c));


let c1 = JSON.parse(c.toJson())

console.log("C! ", c1);

let c2 = Comment.fromJson(c.toJson());

console.log(c2.toJson());
