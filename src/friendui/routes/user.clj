(ns friendui.routes.user
  (:use compojure.core)
  (:require [friendui.views.layout :as layout]
            [ring.util.response :refer [redirect]]
            [cemerick.friend :as friend]
            [clojure.string :as str]
            [friendui.models.user :as user]
            [friendui.models.db :as db]
            [noir.validation :as vali]))

(defn login [& [login_failed]]
  (layout/render "user/login.html"
                 (if login_failed {:error "Wrong username/password combo, or your account is not activated yet."})))

(defn admin []
  (layout/render "user/profile.html"))

(defn validRegister? [email pass confirm]
  (vali/rule (vali/has-value? email)
             [:id "An email address is required"])
  (vali/rule (vali/is-email? email)
             [:id "A valid email is required"])
  (vali/rule (not (user/username-exists email))
             [:id "This username exists in the database. Please choose another one."])
  (vali/rule (vali/min-length? pass 5)
             [:pass "Password must be at least 5 characters"])
  (vali/rule (= pass confirm)
             [:confirm "Entered passwords do not match"])
  (not (vali/errors? :id :pass :confirm)))

(defn signup []
  (layout/render "user/signup.html"
                 {:id-error      (vali/on-error :id first)
                  :pass-error    (vali/on-error :pass first)
                  :confirm-error (vali/on-error :confirm first)}))

(defn account-activated []
  (layout/render "user/account-activated.html"))

(defn activate-account [id]
  (if (not (user/account-activated id))
    (user/activate-account id))
  (friend/merge-authentication
    (redirect "/")
    (user/get-user-for-activation-id id)))


(defn account-created []
  (layout/render "user/account-created.html"))

(defn handle-signup [email password confirm]
  (if (validRegister? email password confirm)
    (do
      (user/create-user email password "free")
      (account-created))
    (signup)
    ))

(defn profile []
  (layout/render "user/profile.html"
                 (println (db/username-kw (friend/current-authentication)))
                 {:fields (user/get-profile-data (:username (friend/current-authentication)))}))

(defn handle-profile [params]
  (user/update-user
    (db/username-kw (friend/current-authentication))
    (select-keys params db/add-profile-keywords))
  (profile))

(defroutes user-routes
           (GET "/user/login" [login_failed] (login login_failed))
           (GET "/user/signup" [] (signup))
           (POST "/user/signup" [email password confirm] (handle-signup email password confirm))
           (GET "/user/accountcreated" [] (account-created))
           (GET "/user/activate/:id" [id] (activate-account id))
           (GET "/user/accountactivated" [] (account-activated))
           (GET "/user/admin" request (friend/authorize #{:admin} (admin)))
           (GET "/user/freetest" [] (friend/authorize #{:free} (account-created)))
           (GET "/user/profile" [] (friend/authenticated (profile)))
           (POST "/user/profile" request (friend/authenticated (handle-profile (:params request))))
           (friend/logout (ANY "/user/logout" [] (redirect "/")))
           )