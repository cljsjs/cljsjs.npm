(ns cljsjs.npm.build
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljsjs.closure :as closure]
            [boot.util :refer [info]]
            [boot.core :as boot]
            [boot.task.built-in :as built-in]
            [cljsjs.npm.util :as util]))

(defn read-json [file]
  (if (.exists file)
    (with-open [r (io/reader file)]
      (json/parse-stream r true))))

(defn parse-version [version-pattern]
  (let [[_ version] (re-find #"[\^](.*)" version-pattern)]
    version))

(defn strip-node-modules [path]
  (string/replace path #"^node_modules/" ""))

(defn package-path [full-path]
  (second (re-find #"^node_modules/[^/]*/(.*)" full-path)))

(defn aname [package-name main? package-path]
  (if main?
    package-name
    (-> package-path
        (string/replace #"\.js$" "")
        (string/replace #"/" "\\$"))))

(defn relative-require? [require-path]
  (.startsWith require-path "."))

(defn package'
  "A Task, but not."
  [{:keys [package-name main files]}]
  (let [out (boot/tmp-dir!)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (info "Package %s\n" package-name)
        (let [files (->> files
                         (map (fn [{:keys [output input deps]}]
                                (io/copy (io/file input)
                                         (doto (io/file out output)
                                           (io/make-parents)))
                                  deps)))]
          (doto (io/file out "deps.cljs")
            (io/make-parents)
            (spit (pr-str {:foreign-libs (vec files)}))))
        (-> fileset
            (boot/add-resource out)
            boot/commit!
            next-handler)))))

(defn generate-stuff' [path]
  (if-let [package-index (read-json (io/file path "package.json"))]
    (let [packages (map name (keys (:dependencies package-index)))
          packages (filter identity (mapcat (fn [package-name]
                                              (when-let [package (some-> (read-json (io/file path "node_modules" package-name "package.json"))
                                                                       (clojure.set/rename-keys {:name :package-name})
                                                                       (select-keys [:package-name :version :main :files :dependencies])
                                                                       (assoc :path path))]
                                                (println "adding" package-name)
                                                (concat [package]
                                                        (generate-stuff' (io/file path "node_modules" package-name)))))
                                            packages))]
      packages)))

(defn generate-stuff [path]
  (let [packages (generate-stuff' path)
        packages (group-by :package-name packages)
        packages (map (fn [[package-name x]]
                        (let [versions (set (map :version x))]
                          (when (seq (rest versions))
                            (println "multiple versions" package-name versions))
                          [package-name (first x)]))
                      packages)]
    (into {} packages)))

(defn read-files [packages]
  (->> (vals packages)
       (map (fn [{:keys [path files package-name main] :as package}]
              (assoc package :files (->> (if files
                                           (conj (mapcat #(file-seq (io/file path "node_modules" package-name %)) files)
                                                 (io/file path "node_modules" package-name "package.json"))
                                           (->> (file-seq (io/file path "node_modules" package-name))
                                                (remove #(re-find (re-pattern (str "^" (.getPath path) "/node_modules/" package-name "/node_modules")) (.getPath %)))))
                                         (filter #(.exists %))
                                         (filter #(or (.endsWith (.getName %) ".js")
                                                      (= (.getName %) "package.json")))
                                         ;; FIXME:
                                         (remove #(.contains (.getName %) "$"))
                                         (map (fn [file]
                                                (let [x-path (.getPath file)
                                                      module-path (string/replace (.getPath file) #"^.*node_modules/" "")
                                                      module-name (aname package-name (= (util/normalize-url (str package-name "/" (or main "index.js"))) module-path) module-path)
                                                      requires (mapv (fn [require-path]
                                                                       (if (relative-require? require-path)
                                                                         (aname package-name false (util/normalize-url (str (util/drop-last-url-part module-path) "/" require-path)))
                                                                         (string/replace require-path #"/" "\\$")))
                                                                     (closure/find-requires file))]
                                                  {:input (.getPath file)
                                                   :output (util/normalize-url (str "cljsjs.npm/node_modules/" module-path))
                                                   :deps {:file (util/normalize-url (str "cljsjs.npm/node_modules/" module-path))
                                                          :provides [module-name]
                                                          :requires requires
                                                          :module-type :commonjs}})))))))
       (map (juxt :package-name identity))
       (into {})))

(defn check-requires [stuff]
  (let [provides (set (mapcat (comp :provides :deps) stuff))
        requires (set (mapcat (comp :requires :deps) stuff))]
    ; (println provides requires)
    (when-let [missing (set/difference requires provides)]
      (println "missing" missing))
    stuff))

(boot/deftask package
  []
  (let [packages-map (read-files (generate-stuff (io/file ".")))
        packages (vals packages-map)]
    (fn middleware [next-handler]
      (let [next-handler (reduce (fn [next-handler {:keys [package-name version path dependencies] :as m}]
                                   (let [middleware (util/with-files (constantly false)
                                                      (comp (package' m)
                                                            (built-in/pom :project (symbol "cljsjs.npm" package-name)
                                                                          :version version
                                                                          :dependencies (map (fn [[k _]]
                                                                                               [(symbol "cljsjs.npm" (name k)) (:version (get packages-map (name k)))])
                                                                                             dependencies))
                                                            (built-in/jar)
                                                            (built-in/show :fileset true)))]
                                     (middleware next-handler)))
                                 next-handler
                                 (reverse packages))]
        (fn handler [fileset]
          (next-handler fileset))))))

(comment
  (check-requires (read-files (generate-stuff (io/file "."))))
  (get (generate-stuff ".") "loose-envify")
  (read-json (io/file "node_modules" "react" "package.json"))
  (boot.core/boot (comp (package) (boot.task.built-in/target) (boot.task.built-in/install))))
