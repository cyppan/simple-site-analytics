(ns track
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["@aws-sdk/client-dynamodb" :as dynamo]
            ["date-fns" :as date-fns]))

(defn today
  "Get the current UTC day as a truncated ISO 8601 string
   ex: 2022-01-29"
  []
  (date-fns/format (js/Date.) "yyyy-MM-dd"))

(def dynamo-client (delay (dynamo/DynamoDBClient. #js{:region "eu-west-3"})))

;; https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-dynamodb/classes/updateitemcommand.html
(defn increment-views [day url utm-source]
  (.send @dynamo-client
         (dynamo/UpdateItemCommand.
          (clj->js {:TableName "SiteStatistics"
                    :Key {:day {:S day}
                          :url {:S url}}
                    :UpdateExpression (str "ADD #views :increment"
                                           (when (seq utm-source)
                                             (str ", views_" utm-source " :increment")))
                    :ExpressionAttributeNames {"#views" "views"}
                    :ExpressionAttributeValues {":increment" {:N "1"}}
                    :ReturnValues "ALL_NEW"}))))

(defn handler [event _ctx]
  (js/console.log (js/JSON.stringify event nil 2))
  (p/let [payload (js/JSON.parse (j/get event :body))
          url (j/get payload :url)
          utm-source (j/get payload :utm_source)
          res (increment-views (today) url utm-source)]
    ;; https://www.serverless.com/blog/cors-api-gateway-survival-guide/
    (clj->js {:statusCode 204
              :headers {"Access-Control-Expose-Headers" "Content-Type"
                        "Access-Control-Allow-Origin" "https://www.loop-code-recur.io"
                        "Access-Control-Allow-Methods" "POST, OPTIONS"}})))

(comment
  (defmacro defp [binding expr]
    `(-> ~expr (.then (fn [val] (def ~binding val))) (.catch (fn [err] (def ~binding err)))))
  (defp resp (increment-views (today) "https://blog.fr/mon-article" "slack"))
  resp
  1)

;; exports
#js {:handler handler}
