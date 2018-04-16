(ns trello.client
  (:require
    [clojure.core.typed :refer [ann cf check-ns U Keyword HMap Val Any Map]]
    [clj-http.client :as client]
    [trello.core :refer [consumer *oauth-token* *oauth-secret*]]
    [oauth.client :as oauth]
    [clojure.data.json :as json])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:refer-clojure :exclude [get]))

;; prevent these from blowing up the type checker
(ann ^:no-check clj-http.client/get [Any * -> Any])
(ann ^:no-check oauth.client/credentials [Any * -> Any])
(ann ^:no-check oauth.client/make-consumer [Any * -> Any])
(ann ^:no-check clojure.data.json/read-str [Any * -> Any])

(ann base-url String)
(def base-url "https://api.trello.com/1/")

(ann authorize-url String)
(def authorize-url "https://trello.com/1/authorize")

(ann full-url [String -> String])
(defn full-url 
  "Get the full api url given an endpoint"
  [endpoint]
  (apply str [base-url endpoint]))

;; not sure about this one...
(ann ^:no-check sign [Any Any Any -> (U Any (HMap :mandatory {:api_key Any} :complete? true))])
(defn sign [method uri params]
  {:author "Daniel Szmulewicz <https://github.com/danielsz>"}
  (if (bound? #'*oauth-token* #'*oauth-secret*)
    (let [consumer (oauth/make-consumer 
                    (:key @consumer) 
                    (:secret @consumer)
                    "https://trello.com/1/OAuthGetRequestToken"
                    "https://trello.com/1/OAuthGetAccessToken"
                    "https://trello.com/1/OAuthAuthorizeToken"
                    :hmac-sha1)]
      (oauth/credentials consumer *oauth-token* *oauth-secret* method uri params))
    {:api_key (:key @consumer)}))

(defn- lowercase-keyword
  "Converts keyword without namespace to lowercase keyword (like :ABC -> :abc)."
  [kw]
  (-> kw
    name
    clojure.string/lower-case
    keyword))

;; not sure about this one
(ann ^:no-check api-call [Keyword String & :optional {:params Any :payload Any} -> Any])
(defn api-call
  "Calls the Trello API with the provided endpoint, HTTP method, and
  params. Returns the response as a Clojure data
  structure (automatically parses the JSON response with
  clojure.data.json)" {:author "Daniel Szmulewicz
  <https://github.com/danielsz>"}
  [method path & {:keys [params payload]}]
  (when-not (seq @consumer) 
    (throw (Throwable. "You must create a consumer first (Trello API key + secret).")))
  (let [uri (str base-url path)
        options {:url uri
                 :cookie-policy :standard
                 :method (lowercase-keyword method)
                 :query-params (merge params (sign method uri params))}]
    (try+
      (-> (client/request options)
        (:body)
        (json/read-str :key-fn keyword))
      (catch [:status 502] {:keys [headers]}
        (println "Trello Unavailable: 502" headers))
      (catch [:status 503] {:keys [headers]}
        (println "Trello Unavailable: 503" headers))
      (catch [:status 504] {:keys [headers]}
        (println "Trello Unavailable: 504" headers))
      (catch Object _
        (println "Unexpected Trello error")
        (throw+)))))

(ann get [String & :optional {:params Map} -> Any])
(defn get [resource & {params :params}]
  (api-call :GET resource :params params))

(ann post [String & :optional {:params Map} -> Any])
(defn post [resource & {params :params}]
  (api-call :POST resource :params params))

(ann put [String & :optional {:params Map} -> Any])
(defn put [resource & {params :params}]
  (api-call :PUT resource :params params))

(ann delete [String & :optional {:params Map} -> Any])
(defn delete [resource & {params :params}]
  (api-call :DELETE resource :params params))
