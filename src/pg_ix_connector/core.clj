(ns ^{:added "0.1.0" :author "Pedro Pereira Santos"}
  pg-ix-connector.core
  "PG -> IX connector"
  (:require [clojure.data.xml :as xml]
            [clojure.core.async :refer [chan <!! >!! close! go <! timeout go-loop]]
            [request-utils.core :as request-utils]
            [result.core :as result]))

(defn optional-element
  "Only produce an element if the value exists"
  [document prop-key]
  (if-let [prop-value (get document prop-key)]
    (xml/element prop-key {} prop-value)))

(defn document-xml
  "Creates a proper XML for the given document data"
  [document]
  (let [client (:client document)]
    (xml/element (keyword (:type document)) {}
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
                                                                 (xml/element :name {} (get-in item [:tax :name]))))))
                                   (:items document))))))

(defn document-xml-str [document]
  (xml/emit-str (document-xml document)))

(def boolean-fields #{:archived})
(def number-fields #{:taxes :total :id :before_taxes :discount :sum :value
                     :unit_price :quantity :tax_amount :subtotal :discount_amount})

(defn add-field [m xml-elem]
  (let [tag (:tag xml-elem)
        raw (first (:content xml-elem))]
    (assoc m tag (cond
                   (some boolean-fields [tag]) (= "true" raw)
                   (some number-fields [tag]) (read-string raw)
                   :else raw))))

(defn xml->map [xml-elements m]
  (reduce (fn [m xml-elem]
            (cond
              (= :client (:tag xml-elem))
              (assoc m :client (xml->map (:content xml-elem) {}))

              (= :tax (:tag xml-elem))
              (assoc m :tax (xml->map (:content xml-elem) {}))

              (= :items (:tag xml-elem))
              (assoc m :items (mapv #(xml->map (:content %) {}) (:content xml-elem)))

              (= :invoice_timeline (:tag xml-elem))
              m

              :else
              (add-field m xml-elem)))
          m
          xml-elements))

(defn load-from-xml
  "Given a XML representation of the document, convert to edn"
  [raw-xml-str]
  (let [xml-data (xml/parse-str raw-xml-str)]
    (result/success (xml->map (:content xml-data) {:type (:tag xml-data)}))))

(defn create-url
  "Gets the url to create the document"
  [args]
  (str "https://" (get-in args [:supplier :account-name])
       ".app.invoicexpress.com/" (name (:type args)) "s"
       "?api_key=" (get-in args [:supplier :api-key])))

(defn change-state-url
  "Gets the url to change the state of the document"
  [args document]
  (str "https://" (get-in args [:supplier :account-name])
       ".app.invoicexpress.com/" (name (:type args)) "s"
       "/" (:id document) "/change-state.xml"
       "?api_key=" (get-in args [:supplier :api-key])))

(defn change-state-body
  "Gets the body for a change state"
  [args state]
  (str "<" (name (:type args)) ">"
        "<state>" state "</state>"
       "</" (name (:type args)) ">"))

(defn create-document-ch
  "Creates an invoicing document, and returns a channel"
  [args]
  (go
    (result/enforce-let [create-response (<! (request-utils/http-post
                                               {:host (create-url args)
                                                :headers {"Content-type" "application/xml; charset=utf-8"}
                                                :plain-body? true
                                                :body (document-xml-str args)}))
                         create-result (load-from-xml (:body create-response))

                         finalize-response (<! (request-utils/http-put
                                               {:host (change-state-url args create-result)
                                                :headers {"Content-type" "application/xml; charset=utf-8"}
                                                :plain-body? true
                                                :body (change-state-body args "finalized")}))
                         finalize-result (load-from-xml (:body finalize-response))]

                        (result/success (merge finalize-result
                                               create-result)))))
