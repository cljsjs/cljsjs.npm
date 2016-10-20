# Cljsjs.npm

# NOT REALLY WORKING YET

Automatically package npm modules as jars with `deps.cljs` files.

## Goals

- Full dead code elimination
- Use React addons as separate dependencies instead of react vs. react-with-addons
- As easy to use as normal Cljsjs packages, or preferably easier
- Easier to package than normal Cljsjs packages

## TODO

- Closure can't yet resolve npm `require`s https://github.com/google/closure-compiler/issues/1773
- Fix Closure, or
- Invent a way to preprocess `require` calls so that Closure can deal with them

## Examples

```$bash
❯ boot package
Package material-ui
Writing pom.xml and pom.properties...
Writing material-ui-0.15.4.jar...
Package react
Writing pom.xml and pom.properties...
Writing react-15.3.1.jar...
Package react-addons-create-fragment
Writing pom.xml and pom.properties...
Writing react-addons-create-fragment-15.3.1.jar...
Package react-addons-transition-group
Writing pom.xml and pom.properties...
Writing react-addons-transition-group-15.3.1.jar...
Package react-dom
Writing pom.xml and pom.properties...
Writing react-dom-15.3.1.jar...
Package react-tap-event-plugin
Writing pom.xml and pom.properties...
Writing react-tap-event-plugin-1.0.0.jar...

~/Source/cljsjs.npm master 8s
❯ tree target
target
├── cljsjs.npm
│   ├── material-ui
│   │   ├── lots of files
│   ├── react
│   │   ├── lib
│   │   │   ├── lots of files
│   │   └── react.js
│   ├── react-addons-create-fragment
│   │   └── index.js
│   ├── react-addons-transition-group
│   │   └── index.js
│   ├── react-dom
│   │   ├── index.js
│   │   └── server.js
│   └── react-tap-event-plugin
│       └── src
│           ├── defaultClickRejectionStrategy.js
│           ├── injectTapEventPlugin.js
│           ├── TapEventPlugin.js
│           └── TouchEventUtils.js
├── deps.cljs
├── material-ui-0.15.4.jar
├── META-INF
│   └── maven
│       └── cljsjs.npm
│           ├── material-ui
│           │   ├── pom.properties
│           │   └── pom.xml
│           ├── react
│           │   ├── pom.properties
│           │   └── pom.xml
│           ├── react-addons-create-fragment
│           │   ├── pom.properties
│           │   └── pom.xml
│           ├── react-addons-transition-group
│           │   ├── pom.properties
│           │   └── pom.xml
│           ├── react-dom
│           │   ├── pom.properties
│           │   └── pom.xml
│           └── react-tap-event-plugin
│               ├── pom.properties
│               └── pom.xml
├── react-15.3.1.jar
├── react-addons-create-fragment-15.3.1.jar
├── react-addons-transition-group-15.3.1.jar
├── react-dom-15.3.1.jar
└── react-tap-event-plugin-1.0.0.jar

81 directories, 1380 files

~/Source/cljsjs.npm master
```
