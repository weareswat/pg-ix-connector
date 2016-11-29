(ns pg-ix-connector.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [chan <!! >!! close! go <! timeout go-loop]]
            [result.core :as result]
            [environ.core :refer [env]]
            [pg-ix-connector.core :refer :all]
            [pg-ix-connector.core :as core]))

(deftest document-xml-test
  (let [data {:type :invoice_receipt
              :date "07/05/2015"
              :due-date "07/05/2015"
              :client {:name "Pedro"
                       :code "123"
                       :fiscal-id "999999990"}
              :items [{:name "Product"
                       :description "Beauty product"
                       :unit-price 5
                       :quantity 1
                       :tax {:name "IVA23"}}]}
        document-str (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                          "<invoice_receipt>"
                            "<date>07/05/2015</date>"
                            "<due_date>07/05/2015</due_date>"
                            "<client>"
                              "<name>Pedro</name>"
                              "<fiscal_id>999999990</fiscal_id>"
                              "<code>123</code>"
                            "</client>"
                            "<items type=\"array\">"
                              "<item>"
                                "<name>Product</name>"
                                "<description>Beauty product</description>"
                                "<unit_price>5</unit_price>"
                                "<quantity>1</quantity>"
                                "<tax>"
                                  "<name>IVA23</name>"
                                "</tax>"
                              "</item>"
                            "</items>"
                          "</invoice_receipt>")]
    (is (= (core/document-xml-str data)
           document-str))))

(def test-account-name
  (or (env :pg-ix-test-account-name)
      (throw (ex-info "No account name in env" {}))))

(def test-api-key
  (or (env :pg-ix-api-key)
      (throw (ex-info "No api-key in env" {}))))

(deftest create-document-test
  (let [data {:supplier {:name "InvoiceXpres"
                         :account-name test-account-name
                         :api-key test-api-key}
              :type :invoice_receipt
              :date "07/05/2015"
              :client {:name "PG IX Connector"
                       :code "pg-ix-connector"}
              :items [{:name "Product"
                       :description "Beauty product"
                       :unit-price 5
                       :quantity 1
                       :tax {:name "IVA23"}}]}
        result (<!! (core/create-document-ch data))]
    (is (result/succeeded? result))
    (is (= "settled" (:state result)))
    (is (slurp (:permalink result)))))

(deftest create-document-change-client-test
  (let [data {:supplier {:name "InvoiceXpres"
                         :account-name test-account-name
                         :api-key test-api-key}
              :type :invoice_receipt
              :date "07/05/2015"
              :client {:name "PG IX Connector"
                       :code "pg-ix-connector"}
              :items [{:name "Product"
                       :description "Beauty product"
                       :unit-price 5
                       :quantity 1
                       :tax {:name "IVA23"}}]}
        result (<!! (core/create-document-ch data))]
    (is (result/succeeded? result))

    (testing "Request with changed client"
      (let [data (-> data
                     (assoc :update-client true)
                     (assoc-in [:client :name] "PG IX Connector Changed")
                     (assoc-in [:client :email] "hello@clanhr.com"))
            result (<!! (core/create-document-ch data))]
        (is (result/succeeded? result))

        (is (= "PG IX Connector Changed" (get-in result [:client :name])))
        (is (= "hello@clanhr.com" (get-in result [:client :email])))))))
