(ns ^{:added "0.1.0" :author "Pedro Pereira Santos"}
  pg-ix-connector.core
  "PG -> IX connector"
  (:require [clojure.data.xml :as xml]))

(defn doc-path
  "Given a document class name, returns it as a path"
  [document]
  (cond
    (= "InvoiceReceipt" (:type document)) "invoice_receipt"
    (= "SimplifiedInvoice" (:type document)) "simplified_invoice"
    (= "Receipt" (:type document)) "receipt"
    :else "invoice"))

(defn optional-element
  "Only produce an element if the value exists"
  [document prop-key]
  (if-let [prop-value (get document prop-key)]
    (xml/element prop-key {} prop-value)))

(defn document-xml
  "Creates a proper XML for the given document data"
  [document]
  (let [client (:client document)]
    (xml/element (keyword (doc-path document)) {}
                 (xml/element :date {} (:date document))
                 (optional-element document :sequence_number)
                 (optional-element document :sequence_id)
                 (optional-element document :reference)
                 (optional-element document :observations)
                 (optional-element document :status)
                 (xml/element :client {}
                              (xml/element :name {} (:name client))
                              (optional-element client :email)
                              (optional-element client :country)
                              (optional-element client :postal_code)
                              (optional-element client :address)
                              (optional-element client :city)
                              (optional-element client :send_options)
                              (optional-element client :website)
                              (optional-element client :phone)
                              (optional-element client :fax)
                              (optional-element client :language)
                              (xml/element :code {} (:code client)))
                 (xml/element :items {:type "array"}
                              (map (fn [item]
                                     (xml/element :item {}
                                                  (xml/element :name {} (:name item))
                                                  (xml/element :description {} (:description item))
                                                  (xml/element :unit_price {} (:unit-price item))
                                                  (xml/element :quantity {} (:quantity item))
                                                  (if (:tax item)
                                                    (xml/element :tax {}
                                                      (xml/element :name {} (:tax item))))))
                                   (:items document))))))

(defn document-xml-str [document]
  (xml/emit-str (document-xml document)))

(defn create-document
  "Creates an invoicing document"
  [args]
  )
