(ns pg-ix-connector.core-test
  (:require [clojure.test :refer :all]
            [pg-ix-connector.core :refer :all]
            [pg-ix-connector.core :as core]))

(deftest document-xml-test
  (is (= (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              "<invoice_receipt>"
                "<date>07/05/2015</date>"
                "<client>"
                  "<name>Pedro</name>"
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
              "</invoice_receipt>")
         (core/document-xml-str {:type "InvoiceReceipt"
                                 :date "07/05/2015"
                                 :client {:name "Pedro"
                                          :code "123"}
                                 :items [{:name "Product"
                                          :description "Beauty product"
                                          :unit-price 5
                                          :quantity 1
                                          :tax "IVA23"}]}))))
