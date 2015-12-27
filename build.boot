(set-env!
  :dependencies '[[adzerk/bootlaces          "0.1.13" :scope "test"]
                  [adzerk/boot-cljs          "1.7.170-3"]
                  [adzerk/boot-reload        "0.4.2"]
                  [compojure                 "1.4.0"]
                  [hoplon/boot-hoplon        "0.1.11"]
                  [hoplon/castra             "3.0.0-alpha3"]
                  [hoplon/hoplon             "6.0.0-alpha11"]
                  [org.clojure/clojure       "1.7.0"]
                  [org.clojure/clojurescript "1.7.189"]
                  [pandeiro/boot-http        "0.7.0"]
                  [ring                      "1.4.0"]
                  [ring/ring-defaults        "0.1.5"]
                  [adzerk/cljs-console       "0.1.1"]]
  :resource-paths #{"src/clj" "src/cljs"})

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-reload    :refer [reload]]
  '[hoplon.boot-hoplon    :refer [hoplon prerender]]
  '[pandeiro.boot-http    :refer [serve]]
  '[adzerk.bootlaces      :refer :all])

(def +version+ "0.0.2")

(bootlaces! +version+ :dont-modify-paths? true)

(task-options!
  pom {:project     'hoplon/notify
       :version     +version+
       :description "Passes notifications via castra to a hoplon client. Uses polling."
       :url         "https://github.com/hoplon/notify"
       :scm         {:url "https://github.com/hoplon/notify"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask dev
  "Build project for development."
  []
  (comp
   (watch)
   (hoplon  :manifest true)
   (build-jar)))

(deftask deploy-release
 "Build for release."
 []
 (comp
   (hoplon :manifest true)
   (build-jar)
   (push-release)))