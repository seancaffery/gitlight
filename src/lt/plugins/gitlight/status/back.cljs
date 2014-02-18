(ns lt.plugins.gitlight.status.back
  (:require [lt.object :as object]
            [lt.objs.tabs :as tabs]
            [lt.objs.proc :as proc]
            [lt.objs.notifos :as notifos]
            [lt.objs.editor.pool :as pool]
            [lt.objs.files :as files]
            [lt.objs.command :as cmd]
            [lt.objs.popup :as popup]
            [clojure.string :as string])
  (:require-macros [lt.macros :refer [defui behavior]]))


(defn get-cwd []
  (if (nil? pool/last-active)
    (let
      [filename (-> @(pool/last-active) :info :path)]
      (files/parent filename))
    (popup/popup! {:header "We couldn't guess git root"
                   :body "Please run `git: status' again with file under git repo in editor tab"
                   :buttons [{:label "ok"}]})))


(defn get-branch [data]
  (second (string/split data #"On branch ")))


(defn delete-inside-parens [data]
  (let [line-with-no-hash (string/replace-first data #"#" " ")
        trimmed (string/trim line-with-no-hash)]
    (string/replace (str trimmed) #"^\(.*\)$" "")))



(defn get-files [data]
  (let [first-line-fix (str "#" data)
        splitted (string/split-lines first-line-fix)
        filtered (filter #(.startsWith %1 "#") splitted)
        no-parens (map delete-inside-parens filtered)]
    (filter #(not (string/blank? %1)) no-parens)))



(defn git-status []
  (let [dir (get-cwd)]
    (proc/exec {:command "/usr/bin/git"
                :args    [:status]
                :cwd     dir
                :obj     shell-git-out})))



(defn split-and-swap [filename]
  (let [[a b] (string/split filename #":" 2)]
    [(string/trim b) (keyword a)]))



(defn split-property [filenames]
  (map split-and-swap filenames))



(defn make-a-category [data]
  (let [cat (first data)
        filenames (rest data)]
    (cond (= cat "to be committed:")       [:to_commit (split-property filenames)]
          (= cat "not staged for commit:") [:not_staged (split-property filenames)]
          (= cat "files:")                 [:untracked (map (fn [f] [f :untracked]) filenames)]
          :else                            [:unknown  (map (fn [f] [f :unknown]) filenames)])))



(defn mangle-data [data]
  (let [splitted (string/split (.toString data) #"#" 3)
        branch-line (second splitted)
        splitted-by-changes (string/split (nth splitted 2) #"Changes|Untracked")
        grouped-by-fields (rest (map get-files splitted-by-changes))]
    {:branch-name (get-branch branch-line)
     :status (into {} (map make-a-category grouped-by-fields))}))
   ; (do
   ;   (println (get-branch branch-line))
   ;   ;(println splitted-by-changes)
   ;   (println (rest (map get-files splitted-by-changes)))
   ;   (println (map make-a-category grouped-by-fields))
   ;   )))

(behavior ::shell-git.out
          :desc "When git command is executed, show its out"
          :triggers #{:proc.out}
          :reaction (fn [ obj data ]
                      (.log js/console "I'm alive")
                      (object/raise obj :status (mangle-data data))))

(def shell-git-out ;shell out object
  (object/create
   (object/object*
    ::shell-git-out
    :tags [:shell-git-out]
    :behaviors [::shell-git.out])))
