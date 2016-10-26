(ns cljsjs.closure
  (:require [clojure.java.io :as io])
  (:import [com.google.javascript.jscomp CompilerPass NodeTraversal
            CompilerOptions SourceFile NodeTraversal$Callback JSModule]
           [com.google.javascript.rhino Node]))

(defn is-require? [n]
  (and (= 2 (.getChildCount n))
       (.. n getFirstChild (matchesQualifiedName "require"))
       (.. n getSecondChild isString)))

(defn finder [requires]
  (reify NodeTraversal$Callback
    (shouldTraverse ^boolean [this t n parent]
      true)
    (visit ^void [this t n parent]
      (if (is-require? n)
        (swap! requires conj (.. n getSecondChild getString)))
      nil)))

(defn process-pass [compiler requires]
  (reify CompilerPass
    (process [this _ root]
      (NodeTraversal/traverseEs6 compiler root (finder requires)))))

(defn find-requires [f]
  (let [requires (atom [])
        module (doto (JSModule. "$singleton$")
                 (.add (SourceFile/fromFile f)))
        closure-compiler (com.google.javascript.jscomp.Compiler.)
        pass (process-pass closure-compiler requires)]
    (doseq [input (.getInputs module)]
      (.process pass nil (.getAstRoot input closure-compiler)))
    @requires))

(comment
  (find-requires (io/file "node_modules/react/lib/React.js")))
