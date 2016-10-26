(ns cljsjs.npm.build
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cljsjs.closure :as closure]
            [boot.util :as util]
            [boot.core :as boot]
            [boot.task.built-in :as built-in]))

(defn read-json [file]
  (with-open [r (io/reader file)]
    (json/parse-stream r true)))

(defn parse-version [version-pattern]
  (let [[_ version] (re-find #"[\^](.*)" version-pattern)]
    version))

(defn copy-main [out {:keys [name main]}]
  (io/copy (io/file "node_modules" name main)
           (doto (io/file out "cljsjs.npm" name main)
             (io/make-parents))))

(defn with-files
  "Runs middleware with filtered fileset and merges the result back into complete fileset."
  [p middleware]
  (fn [next-handler]
    (fn [fileset]
      (let [merge-fileset-handler (fn [fileset']
                                    (next-handler (boot/commit! (assoc fileset :tree (merge (:tree fileset) (:tree fileset'))))))
            handler (middleware merge-fileset-handler)
            fileset (assoc fileset :tree (reduce-kv
                                          (fn [tree path x]
                                            (if (p x)
                                              (assoc tree path x)
                                              tree))
                                          (empty (:tree fileset))
                                          (:tree fileset)))]
        (handler fileset)))))

(defn package'
  "A Task, but not."
  [{:keys [name main]}]
  (let [out (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (util/info "Package %s\n" name)
        (let [files (->> (file-seq (io/file "node_modules" name))
                         (remove #(re-find #"^node_modules/.*/node_modules" (.getPath %)))
                         (remove #(re-find #"^node_modules/.*/dist" (.getPath %)))
                         (filter #(.endsWith (.getName %) ".js"))
                         (map (fn [file]
                                (let [path (.getPath file)
                                      [_ package-path] (re-find #"^node_modules/[^/]*/(.*)" path)
                                      aname (if (= main package-path)
                                              name
                                              (str name "/" (string/replace package-path #"\.js$" "")))
                                      aname (string/replace aname #"/" "\\$")]
                                  (io/copy file (doto (io/file out "cljsjs.npm" (string/replace path #"^node_modules/" "")) (io/make-parents)))
                                  {:file (str "cljsjs.npm/" (string/replace path #"^node_modules/" ""))
                                   :provides [aname]
                                   :module-type :commonjs}))))]
          (doto (io/file out "deps.cljs")
            (io/make-parents)
            (spit (pr-str {:foreign-libs (vec files)}))))
        (-> fileset
            (boot/add-resource out)
            boot/commit!
            next-handler)))))

(boot/deftask package
  []
  (let [package-index (read-json (io/file "./package.json"))
        packages (map name (keys (:dependencies package-index)))]
    (fn middleware [next-handler]
      (let [next-handler (reduce (fn [next-handler package-name]
                                   (let [package-json (read-json (io/file "node_modules" package-name "package.json"))
                                         middleware (with-files (constantly false)
                                        (comp (package' package-json)
                                              (built-in/pom :project (symbol "cljsjs.npm" package-name)
                                                            :version (:version package-json))
                                              (built-in/jar)
                                              (built-in/show :fileset true)))]
                                     (middleware next-handler)))
                                 next-handler
                                 (reverse packages))]
        (fn handler [fileset]
          (next-handler fileset))))))

(comment
  (read-json (io/file "node_modules" "react" "package.json"))
  (boot.core/boot (comp (package) (boot.task.built-in/target))))
