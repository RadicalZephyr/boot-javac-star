## DEPRECATED

You probably want to use [virgil](https://github.com/ztellman/virgil#boot) instead.

# boot-javac*

[](dependency)
```clojure
[radicalzephyr/boot-javac-star "0.1.0-SNAPSHOT"] ;; latest release
```
[](/dependency)

This task intends to be a drop-in replacement for the built-in `javac`
task, with the additional feature that it aims to support propagating
changes to the Java sources it compiles into changes to those classes
inside the currently running JVM process. It's not quite plug-and-play
yet, but that's the eventual goal.

- Provides a `javac*` task

## Usage

Still working on this.  For now, it should be 100% usage compatible
with the built-in `javac` task.  Using it and getting reloading may be
more complicated (especially between Pods.  As in, I don't think it
will work to reload classes in a different pod than the main boot
environment).

## License

Copyright © 2016 Geoff Shannon

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
