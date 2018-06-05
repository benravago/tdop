## A different style for writing Pratt parsers.

Some time ago, I found Andy Chu's blog post about [a style of writing Pratt parsers](http://www.oilshell.org/blog/2016/11/03.html) that he worked out after [reviewing other such implementations](https://www.oilshell.org/blog/2016/11/02.html).  I found his approach to be a lot simpler and more straightforward and, since I write code primarily in Java, thought that it would be useful to port.

The *pratt-parsing-demo/* directory contains my conversion of his Python [demo code](https://github.com/andychu/pratt-parsing-demo), following the original as much as possible to make the correspondence clear.  To run the demo, checkout the repo, compile the *pratt-parsing-demo/pp/\*.java* files, then launch <code>pp.Tests</code>; expect output as in *pratt-parsing-demo/results.txt*.

The *src/* and *test/* directories contain a restructured java port of the demo code.  This port separates the TDOP api and the Parser driver code and also includes a more extensive Tokenizer.  The *test/{Arith,Tests}.java* files correspond to the *pratt-parsing-demo/{ArithParse,Tests}.java* files and similarly provide a basic example.

Eventually, I hope to add an additional language implementation using the techniques shown here.
