## A different style for writing Pratt parsers.

Some time ago, I found Andy Chu's blog post about [a style of writing Pratt parsers](http://www.oilshell.org/blog/2016/11/03.html) that he worked out after [reviewing other such implementations](https://www.oilshell.org/blog/2016/11/02.html).  I found his approach to be a lot simpler and more straightforward and, since I write code primarily in Java, thought that it would be useful to port.

To start with, I've converted his Python [demo code](https://github.com/andychu/pratt-parsing-demo), following the original as much as possible to make the correspondence clear.  To run the demo, checkout the repo, <code>cd pratt-parser-demo</code>, compile the code with <code>javac pp/Tests.java</code>, then <code>java pp.Tests</code> to run the test suite; expect output as in *pratt-parsing-demo/results.txt*.


Eventually, I hope to add an additional language implementation using the techniques shown here.
