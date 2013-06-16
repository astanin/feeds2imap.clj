(ns feeds2imap.feeds
  (:require [hiccup.core :refer :all]
            [feedparser-clj.core :refer :all]
            [feeds2imap.message :as message]
            [clojure.pprint :refer :all]))

(defn ^:private map-items
  "Map function over items for each folder."
  [fun coll]
  (map (fn [[folder items]] [folder (map fun items)]) coll))

(defn ^:private filter-items
  "Filter items for each folder.
   Filter folders with non empty items collection."
  [fun coll]
  (->> coll
       (map (fn [[folder items]]
              [folder (filter fun items)]))
       (filter (fn [[folder items]]
                 (not (empty? items))))))

(defn ^:private flattern-items [items]
  (map (fn [[folder emails]]
        [folder (flatten emails)])
       items))

(defn ^:private item-authors [{:keys [authors]}]
  (apply str (map (fn [author]
                    (str (:name author)
                         " | "
                         (:email  author)
                         " | "
                         (:uri   author))) authors)))

(defn ^:private digest
  "Generates unique digest for item."
  [{:keys [title link] :as item}]
  (hash (str title link (item-authors item))))

(defn ^:private new?
  "Looks up item in the cache"
  [cache item]
  (not (contains? cache (digest item))))

(defn mark-all-as-read
  "Adds all items to cache.
   Returns updated cache."
  [cache items]
  (->> items
       (map last)
       flatten
       (map digest)
       (into cache)))

;TODO use hiccup here, extract all data from item
(defn to-email-map
  "Convert item to map for Message construction."
  [from to item]
  (let [{:keys [title link]} item
        authors (item-authors item)
        content (-> item :contents first :value)
        html (html [:table
                     [:tbody [:tr [:td [:a {:href link} title] [:hr]]]
                             [:tr [:td authors [:hr]]]
                             [:tr [:td content]]]])]
    {:from from :to to :subject title :html html}))

(defn items-to-emails [session from to item]
  (message/from-map session (to-email-map from to item)))

(defn to-emails
  "Convert items to Messages."
  [session from to items]
  (->> items
       (map-items (partial items-to-emails session from to))))

(defn new-items [cache urls]
  (->> urls
       (map-items parse-feed)
       (map-items :entries)
       (flattern-items)
       (filter-items (partial new? cache))))