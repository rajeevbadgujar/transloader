It may not be _every_ day you need to use an object built via a `ClassLoader` that differs from your current `ClassLoader`... but when you do... _this_ is the help you need!

Transloader makes two things easy to do:

1. Clone almost any object graph from one `ClassLoader` to another

2. Take any object from a foreign `ClassLoader` and invoke any method on it without cloning it

Transloader can **deeply clone** almost any object (not just `Serializable`s! not just with zero-arg constructors!! even with circular references!!!) from one `ClassLoader` to another, providing various hooks to adjust how this is done. The cloned object can then be used as any other, as if it had never come from another `ClassLoader`. The only current restriction is that to be clonable on pre-Java 5 JVMs, any non-`Serializable` objects in the graph must not have `final` fields.

If you don't want to clone but just use the object itself, Transloader can also **invoke methods** on an uncloned, foreign-`ClassLoader` object. Transloader takes care of: finding the desired `Method` (as the parameter types can differ between `ClassLoader`s); cloning the given parameters as necessary; and invoking the method with the potentially cloned parameters.

Originally conceived as a way to use OSGi Services from outside their host Framework, Transloader has been made available without specific ties to OSGi, in case it might be useful elsewhere. It also happens to provide powerful cloning and reflection facilities that are generally useful, not only when crossing `ClassLoader`s.

[Maven Site](http://transloader.googlecode.com/svn/wiki/maven/site/index.html)