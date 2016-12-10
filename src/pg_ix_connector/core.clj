(ns ^{:added "0.1.0" :author "Pedro Pereira Santos"}
  pg-ix-connector.core
  "PG -> IX connector"
  (:require [clojure.data.xml :as xml]
            [clojure.core.async :refer [chan <!! >!! close! go <! timeout go-loop]]
            [request-utils.core :as request-utils]
            [result.core :as result]))

(defn input-key
  "Replace underscore for hyphen"
  [key]
  (let [key-string (name key)]
    (if (clojure.string/includes? key-string "_")
      (keyword (clojure.string/replace key-string "_" "-"))
      key)))

(defn optional-element
  "Only produce an element if the value exists"
  [document prop-key]
  (if-let [prop-value (get document (input-key prop-key))]
    (xml/element prop-key {} prop-value)))

(defn client-xml
  "Creates a proper XML for the given client"
  [client]
  (xml/element :client {}
               (xml/element :name {} (:name client))
               (optional-element client :fiscal_id)
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
               (xml/element :code {} (:code client))))

(defn document-xml
  "Creates a proper XML for the given document data"
  [document]
  (let [client (:client document)]
    (xml/element (keyword (:type document)) {}
                 (xml/element :date {} (:date document))
                 (xml/element :due_date {} (:due-date document))
                 (optional-element document :tax-exemption)
                 (optional-element document :sequence_number)
                 (optional-element document :sequence_id)
                 (optional-element document :reference)
                 (optional-element document :observations)
                 (optional-element document :status)
                 (client-xml client)
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

(defn client-xml-str [client]
  (xml/emit-str (client-xml client)))

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

(defn host
  "Gets the api host"
  [args]
  (or (get-in args [:supplier :host])
      "app.invoicexpress.com"))

(defn create-url
  "Gets the url to create the document"
  [args]
  (str "https://" (get-in args [:supplier :account-name])
       "." (host args) "/" (name (:type args)) "s"
       "?api_key=" (get-in args [:supplier :api-key])))

(defn client-by-code-url
  "Gets the url to get the client by code"
  [args]
  (str "https://" (get-in args [:supplier :account-name])
       "." (host args) "/clients/find-by-code.xml"
       "?api_key=" (get-in args [:supplier :api-key])
       "&client_code=" (get-in args [:client :code])))

(defn update-client-url
  "Gets the url to update the client"
  [args client-id]
  (str "https://" (get-in args [:supplier :account-name])
       "." (host args) "/clients/" (str client-id) ".xml"
       "?api_key=" (get-in args [:supplier :api-key])))

(defn change-state-url
  "Gets the url to change the state of the document"
  [args document]
  (str "https://" (get-in args [:supplier :account-name])
       "." (host args) "/" (name (:type args)) "s"
       "/" (:id document) "/change-state.xml"
       "?api_key=" (get-in args [:supplier :api-key])))

(defn change-state-body
  "Gets the body for a change state"
  [args state]
  (str "<" (name (:type args)) ">"
        "<state>" state "</state>"
       "</" (name (:type args)) ">"))

(defn update-client!
  "Updates the client data, if necessary"
  [args]
  (go
    (if-not (:update-client args)
      (result/success)
      (result/enforce-let [client (<! (request-utils/http-get
                                        {:host (client-by-code-url args)
                                         :plain-body? true
                                         :headers {"Content-type" "application/xml; charset=utf-8"}}))

                           update-result (<! (request-utils/http-put
                                          {:host (update-client-url args
                                                                    (-> client
                                                                        :body
                                                                        load-from-xml
                                                                        :id))
                                           :plain-body? true
                                           :body (client-xml-str (:client args))
                                           :headers {"Content-type" "application/xml; charset=utf-8"}}))]))))

(defn create-document-ch
  "Creates an invoicing document, and returns a channel"
  [args]
  (go
    (result/enforce-let [client-result (<! (update-client! args))

                         create-response (<! (request-utils/http-post
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
