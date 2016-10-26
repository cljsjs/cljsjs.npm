(set-env!
  :dependencies '[[cheshire "5.6.3"]
                  [com.google.javascript/closure-compiler-unshaded "v20160911"]]
  :source-paths #{"src"})

(require '[cljsjs.npm.build :refer [package]])
