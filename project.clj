(defproject pg-ix-connector "0.2.0"
  :description "Connects the payment gateway to InvoiceXpress"
  :url "https://github.com/weareswat/pg-ix-connector"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [environ "1.1.0"]
                 [weareswat/request-utils "0.5.0"]]

  :aliases {"autotest" ["trampoline" "with-profile" "+test" "test-refresh"]
            "test"  ["trampoline" "test"]}

  :profiles {:dev {:env {:dev "true"}
                   :global-vars {*warn-on-reflection* false
                                 *assert* true}
                   :plugins [[com.jakemccrary/lein-test-refresh "0.14.0"]]}}

  :test-refresh {:quiet true
                 :changes-only true})
