(ns statistics
  (:require [promesa.core :as p]
            [reagent.dom.server :as srv]
            ["@aws-sdk/client-dynamodb" :as dynamo]
            ["date-fns" :as date-fns]
            ["crypto" :as crypto]))

(def dynamo-client (delay (dynamo/DynamoDBClient. #js{:region "eu-west-3"})))

(defn last-7-days
  "get the last 7 days as yyyy-MM-dd strings"
  []
  (for [day (->> (js/Date.)
                 (iterate #(date-fns/subDays % 1))
                 (take 7)
                 (reverse))]
    (date-fns/format day "yyyy-MM-dd")))

(defn -parse-dynamo-item
  "{:day {:S \"2021-01-30\"} :views {:N 3}}
   =>
   {:day \"2021-01-30 \" :views 3}"
  [item]
  (->> item
       (map (fn [[k v]]
              [k (case (-> v first key)
                   :S (-> v first val)
                   :N (-> v first val js/parseInt))]))
       (into {})))

;; https://docs.aws.amazon.com/AWSJavaScriptSDK/v3/latest/clients/client-dynamodb/classes/querycommand.html
(defn fetch-last-7-days-statistics
  "returns [{day url views}]"
  []
  (p/let [items (js/Promise.all
                 (for [day (last-7-days)]
                   (p/let [resp (.send @dynamo-client
                                       (dynamo/QueryCommand.
                                        (clj->js {:TableName "SiteStatistics"
                                                  :KeyConditionExpression "#day = :day"
                                                  :ExpressionAttributeNames {"#day" "day"}
                                                  :ExpressionAttributeValues {":day" {:S day}}})))
                           resp (js->clj resp :keywordize-keys true)
                           items (->> (:Items resp)
                                      (map -parse-dynamo-item))]
                     (or (seq items) [{:day day :views 0}]))))]
    (into [] cat items)))

(defn counter-cards [stat-rows]
  (let [views (reduce + 0 (map :views stat-rows))
        views-slack (reduce + 0 (map :views_slack stat-rows))
        views-twitter (reduce + 0 (map :views_twitter stat-rows))]
    [:nav.level.is-mobile
     [:div.level-item.has-text-centered
      [:div
       [:p.heading "Total views"]
       [:p.title views]]]
     [:div.level-item.has-text-centered
      [:div
       [:p.heading "views from Slack"]
       [:p.title views-slack]]]
     [:div.level-item.has-text-centered
      [:div
       [:p.heading "views from Twitter"]
       [:p.title views-twitter]]]]))

;; https://vega.github.io/vega-lite/usage/embed.html
(defn views-bar-chart [stat-rows]
  (let [data (->> stat-rows
                  (group-by :day)
                  (map (fn [[day rows]]
                         {:day day
                          :views (reduce + 0 (map :views rows))}))
                  (sort-by :day <))
        spec (clj->js {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
                       :data {:values data}
                       :mark {:type "bar"}
                       :width "container"
                       :height 300
                       :encoding {:x {:field "day"
                                      :type "nominal"
                                      :axis {:labelAngle -45}}
                                  :y {:field "views"
                                      :type "quantitative"}}})
        id (str "div-" (.toString (crypto/randomBytes 16) "hex"))
        raw (str "<div id=\"" id "\" style=\"width:100%;height:300px\"></div>"
                 "<script type=\"text/javascript\">"
                 "vegaEmbed ('#" id "', JSON.parse('" (js/JSON.stringify spec) "'));"
                 "</script>")]
    [:div {:dangerouslySetInnerHTML {:__html raw}}]))

(defn top-urls-table [stat-rows]
  (let [top-urls (->> stat-rows
                      (filter :url)
                      (group-by :url)
                      (map (fn [[url rows]]
                             {:url url
                              :views (reduce + 0 (map :views rows))
                              :views_slack (reduce + 0 (map :views_slack rows))
                              :views_twitter (reduce + 0 (map :views_twitter rows))}))
                      (sort-by :views >))]
    [:table.table.is-fullwidth.is-hoverable.is-striped
     [:thead>tr
      [:th "Rank"]
      [:th "URL"]
      [:th "Views"]
      [:th "Slack"]
      [:th "Twitter"]]
     [:tbody
      (for [[i {:keys [url views views_slack views_twitter]}] (map-indexed vector top-urls)]
        [:tr
         [:th {:style {:width "20px"}} (inc i)]
         [:td [:a {:href url} url]]
         [:td {:style {:width "20px"}} views]
         [:td {:style {:width "20px"}} views_slack]
         [:td {:style {:width "20px"}} views_twitter]])]]))

(defn wrap-template [hiccup]
  (str "<!doctype html>
        <html lang=\"en\">
        <head>
          <meta charset=\"utf-8\">
          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
          <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css\">
          <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.4/css/all.min.css\" integrity=\"sha512-1ycn6IcaQQ40/MKBW2W4Rhis/DbILU74C1vSrLJxCq57o941Ym01SwNsOMqvEBFlcgUa6xLiPY/NS5R+E6ztJQ==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\" />
          <link rel=\"icon\" type=\"image/png\" href=\"data:image/png;base64,iVBORw0KGgo=\">
          <script src=\"https://cdn.jsdelivr.net/npm/vega@5.21.0\"></script>
          <script src=\"https://cdn.jsdelivr.net/npm/vega-lite@5.2.0\"></script>
          <script src=\"https://cdn.jsdelivr.net/npm/vega-embed@6.20.2\"></script>
          <title>Site Analytics</title>
        </head>
        <body>
          <section class=\"hero is-link block\">
            <div class=\"hero-body has-text-centered\">
              <p class=\"title\" style=\"vertical-align:baseline\">
                <span class=\"icon\">
                  <i class=\"fas fa-chart-pie\"></i>
                </span>
                &nbsp;Site Analytics
              </p>
              <p class=\"subtitle\">last 7 days</p>
            </div>
          </section>
          <div class=\"container is-max-desktop\">
        "
       (srv/render-to-static-markup hiccup)
       "  </div>
        </body>
        </html>"))

(defn handler [event _ctx]
  (p/let [stat-rows (fetch-last-7-days-statistics)]
    (clj->js
     {:statusCode 200
      :headers {"Content-Type" "text/html"}
      :body (wrap-template
             [:<>
              [:div.box
               (counter-cards stat-rows)
               (views-bar-chart stat-rows)]
              [:div.box
               [:h1.title.is-3 "Top URLs"]
               (top-urls-table stat-rows)]])})))

(comment
  (defmacro defp [binding expr]
    `(-> ~expr (.then (fn [val] (def ~binding val))) (.catch (fn [err] (def ~binding err)))))
  (defp stat-rows (fetch-last-7-days-statistics))
  stat-rows
  (println (wrap-template
            [:<>
             [:div.box
              (counter-cards stat-rows)
              (views-bar-chart stat-rows)]
             [:div.box
              [:h1.title.is-3 "Top URLs"]
              (top-urls-table stat-rows)]])))

;; exports
#js {:handler handler}