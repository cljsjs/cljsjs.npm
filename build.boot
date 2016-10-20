(set-env!
  :dependencies '[[cheshire "5.6.3"]]
  :source-paths #{"src"})

(require '[cljsjs.npm.build :refer [package]])
