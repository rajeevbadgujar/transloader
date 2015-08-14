#labels Featured
# The Problem #

Suppose you have one object, built using [Class](http://java.sun.com/j2se/1.3/docs/api/java/lang/ClassLoader.html)es loaded through one [ClassLoader](http://java.sun.com/j2se/1.3/docs/api/java/lang/ClassLoader.html), and another object, built using `Class`es loaded through a `ClassLoader` that is _independent_ of the first `ClassLoader`, and you want the two objects to interact:

![http://transloader.googlecode.com/svn/wiki/images/ClassLoaderClash.png](http://transloader.googlecode.com/svn/wiki/images/ClassLoaderClash.png)

This scenario does not occur very often in normal Java™ development. However, it does occur whenever a container you are using implements `ClassLoader` isolation and yet that isolation needs to be crossed for whatever reason. For example, with [OSGi](http://www.osgi.org), which provides `ClassLoader` isolation for each Bundle (a module in the OSGi Framework), valid reasons for crossing the `ClassLoader` divide include:

  1. You have an object from a Bundle in the OSGi Framework that you want to use _outside_ the OSGi Framework.
  1. You need to work around the situation where a particular combination of Bundle constraints makes it impossible for the OSGi Framework to make the “Class space” consistent.

The interaction between two Objects in the scenario above faces two potential problems:
  1. ClassLoader1 doesn’t even have access to the same `Class` definitions as ClassLoader2
    * Effect: You cannot even compile the `Class` of ObjectA to reference the `Class` of ObjectB in the first place.
    * Workaround: You could use [Java™’s Reflection API](http://java.sun.com/j2se/1.3/docs/guide/reflection/index.html) to call methods on ObjectB but this can be difficult and is always ugly.
  1. ClassLoader1 has access to the same `Class` definitions as ClassLoader2 but loads them independently
    * Effect: You can compile the `Class` of ObjectA to reference the `Class` of ObjectB but when you bring the objects into contact (e.g. by passing one into the other through a reflective method invocation, or pulling one out of a [Collection](http://java.sun.com/j2se/1.3/docs/api/java/util/Collection.html) accessible to the other and casting it) then a [ClassCastException](http://java.sun.com/j2se/1.3/docs/api/java/lang/ClassCastException.html) will be thrown. This is because although ObjectB appears to extend/implement a type known to ObjectA, it really extends/implements a different copy of that type loaded by a different `ClassLoader`, not the copy of the type known to ObjectA.
    * Workaround: Again, don’t use ObjectB directly but either invoke its methods reflectively, with all the drawbacks that go with Java™’s Reflection API, or use [Java™’s Serialization API](http://java.sun.com/j2se/1.3/docs/guide/serialization/index.html) to serialize ObjectB and deserialize it using ClassLoader1. The latter workaround has the advantage of making a copy of ObjectB that will work in [Object#equals(Object)](http://java.sun.com/j2se/1.3/docs/api/java/lang/Object.html#equals(java.lang.Object)) and so forth but is slow and only works for object graphs that are completely [Serializable](http://java.sun.com/j2se/1.3/docs/api/java/io/Serializable.html).

# The Solution #

Transloader aims to provide superior alternatives to the workarounds above.

The central interface is [com.googlecode.transloader.Transloader](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/Transloader.html) and for convenience it provides static access to the default implementation via the [Transloader.DEFAULT](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/Transloader.html#DEFAULT) constant (but you can use whatever implementation you want, which you'll usually want to inject following IOC). The `Transloader` interface is used to wrap objects referencing `Class`es from potentially foreign `ClassLoader`s like so:
```
Object someObject = someService.getObjectFromAnotherClassLoader();
Transloader transloader = Transloader.DEFAULT;
ObjectWrapper someObjectWrapped = transloader.wrap(someObject);
ClassWrapper someClassWrapped = transloader.wrap(someObject.getClass());
```

The resulting wrappers can then be used to work with the wrapped object despite the wrapped object referencing different `Class`es to those loaded by the `ClassLoader` of the calling code.

The [ClassWrapper](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/ClassWrapper.html) is fairly self-explanatory; of more interest is the [ObjectWrapper](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/ObjectWrapper.html). `ObjectWrapper` basically provides the ability to clone the wrapped object in a way far superior to serialization or to invoke methods on the wrapped object without resorting directly to Java™’s Reflection API. It also provides the ability to find out about the wrapped object before doing either:
```
if (someObjectWrapped.isNull()) …
```
will let you handle the null case while
```
if (someObjectWrapped.isInstanceOf(“com.somepackage.SomeType”)) …
```
will let you handle particular types of object, analogous to
```
if (someObject instanceOf SomeType)) …
```
except that the [isInstanceOf](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/ObjectWrapper.html#isInstanceOf(java.lang.String)) method takes a [String](http://java.sun.com/j2se/1.3/docs/api/java/lang/String.html), not a `Class`, to indicate that it is only matching the wrapped object against a type _name_ which could actually be loaded by any `ClassLoader` (whereas a `Class` refers to a specific `ClassLoader`) and in fact a definition of that type need not even be accessible to the `ClassLoader` of the calling object.

## Cloning ##
If you want a copy of the wrapped object that will behave _exactly_ as if it were originally constructed using `Class`es from the current `ClassLoader`, the [cloneWith](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/ObjectWrapper.html#cloneWith(java.lang.ClassLoader)) method is your friend:
```
if (someObjectWrapped.isInstanceOf(“com.somepackage.SomeType”)) {
    SomeType someObject = (SomeType) someObjectWrapped.cloneWith(getClass().getClassLoader());
    if (someOtherObject.equals(someObject) …
}
```
This is helpful when you are interested in the _values_ of the object from a foreign `ClassLoader`, not in that precise `Object` itself. It is perfectly compatible, for example, with being passed into the `Object#equals(Object)` method because it is built using local `Class`es. Of course, this can only solve Problem 2 above, where the relevant `Class`es are accessible to both `ClassLoader`s, not Problem 1 where the current `ClassLoader` cannot even access definitions of the `Class`es in the foreign `ClassLoader`.

The algorithm used to create the clone is supplied by the [CloningStrategy](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/CloningStrategy.html) injected into the `ObjectWrapper` at [construction](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/ObjectWrapper.html#ObjectWrapper(java.lang.Object,%20com.googlecode.transloader.clone.CloningStrategy)), which in turn can be supplied by the particular implementation of `Transloader` that you choose to use. You can change the `CloningStrategy` by replacing the implementation of `Transloader` that you are using:
```
Transloader transloader = new DefaultTransloader(CloningStrategy.MINIMAL);
```
Out of the box Transloader supplies two major implementations of `CloningStrategy`
  1. [One](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/SerializationCloningStrategy.html) based on Java™ Serialization, which is comparatively slow and limited only to `Serializable`s, but has the advantage that Java™ Serialization has at least been well understood for a long time.
  1. [One](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/reflect/ReflectionCloningStrategy.html) based on Java™ Reflection, which is comparatively fast and much more flexible.

The latter is divided into two flavours by different implementations of [CloningDecisionStrategy](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/reflect/CloningDecisionStrategy.html). [MinimalCloningDecisionStrategy](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/reflect/MinimalCloningDecisionStrategy.html) actually only clones an object if it instantiates a `Class` that happens to be different when loaded through the `ClassLoader` supplied to `cloneWith(ClassLoader)`; everything else is left untouched. [MaximalCloningDecisionStrategy](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/clone/reflect/MaximalCloningDecisionStrategy.html) clones everything. `MinimalCloningDecisionStrategy` is usually the best choice because it is _so_ fast and less likely to hit problems, because it is attempting to do the least. However, it can provide surprising results if you do not understand how it works:

![http://transloader.googlecode.com/svn/wiki/images/ShallowClone.png](http://transloader.googlecode.com/svn/wiki/images/ShallowClone.png)

In the scenario above, [CloningStrategy.MINIMAL](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/clone/CloningStrategy.html#MINIMAL) will determine that ObjectC extends a `Class` that is the same whether loaded through ClassLoader1 or ClassLoader2 and therefore does not clone it. In this scenario, `CloningStrategy.MINIMAL` is not too surprising as it’s just doing a shallow clone instead of the deep clone that `SerializationCloningStrategy` or [CloningStrategy.MAXIMAL](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/clone/CloningStrategy.html#MAXIMAL) would do. However, imagine the scenario where ObjectC is the top level object that references ObjectA:

![http://transloader.googlecode.com/svn/wiki/images/AlteringClone.png](http://transloader.googlecode.com/svn/wiki/images/AlteringClone.png)

In this scenario, ObjectC is again not cloned and, again, ObjectA is. However, because ObjectC is now the top level object that is wrapped by `ObjectWrapper`, the `cloneWith` method now actually returns the wrapped object itself (because it wasn’t cloned) while the wrapped object itself has been _altered_ to now refer to the clone of ObjectA, which is ObjectB, instead of referring to ObjectA. So rather than being a completely new, clean copy, `CloningStrategy.MINIMAL` will, in this scenario, alter the wrapped object just enough to make it compatible with `Class`es from the given `ClassLoader`. If this is not what you want, then use `CloningStrategy.MAXIMAL`, which happens to be the default in `Transloader.DEFAULT`.

## Method Invocation ##

If you need to invoke on the wrapped object a method that is not on an `interface` you have access to in the current `ClassLoader`, here’s what to do:
```
someObjectWrapped.invoke(new InvocationDescription(“getValue”))
```
There is only one [invoke](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/ObjectWrapper.html#invoke(com.googlecode.transloader.InvocationDescription)) method but [InvocationDescription](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/index.html?com/googlecode/transloader/InvocationDescription.html) has many convenient constructors for specifying invocations with zero, one or many parameters. These parameters are cloned on the way through to ensure no `ClassCastException`s as a result of the different `ClassLoader`s involved. The `CloningStrategy` employed will be the one injected into the `ObjectWrapper` at construction.

If you are fortunate enough that the method you want to invoke happens to exist on an `interface` accessible to your current `ClassLoader`, then you’ll prefer:
```
SomeInterface someObject = (SomeInterface) someObjectWrapped.makeCastableTo(SomeInterface.class);
someObject.doSomethingWith(parameterObject);
```
The [makeCastableTo](http://transloader.googlecode.com/svn/wiki/maven/site/apidocs/com/googlecode/transloader/ObjectWrapper.html#makeCastableTo(java.lang.Class)) method just returns a proxy that implements the given `interface` (which would be loaded through the current `ClassLoader`) and delegates to the `ObjectWrapper#invoke` method under the covers.

In this way the object from the foreign `ClassLoader` can be employed to do work using all its current references to other objects, which is particularly handy if, for example, you want to invoke an OSGi Service from outside the OSGi Framework.