/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.mongodb;

import org.bson.BsonArray;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.realm.RealmAsyncTask;
import io.realm.annotations.Beta;
import io.realm.internal.Util;
import io.realm.internal.jni.OsJNIResultCallback;
import io.realm.internal.jni.OsJNIVoidResultCallback;
import io.realm.internal.mongodb.Request;
import io.realm.internal.network.ResultHandler;
import io.realm.internal.objectstore.OsJavaNetworkTransport;
import io.realm.internal.objectstore.OsMongoClient;
import io.realm.internal.objectstore.OsPush;
import io.realm.internal.objectstore.OsSyncUser;
import io.realm.internal.util.Pair;
import io.realm.mongodb.auth.ApiKeyAuth;
import io.realm.mongodb.functions.Functions;
import io.realm.mongodb.mongo.MongoClient;
import io.realm.mongodb.mongo.StreamNetworkTransport;
import io.realm.mongodb.push.Push;

/**
 * A <i>user</i> holds the user's meta data and tokens for accessing Realm App functionality.
 * <p>
 * The user is used to configure Synchronized Realms and gives access to calling Realm App <i>Functions</i>
 * through {@link Functions} and accessing remote Realm App <i>Mongo Databases</i> through a
 * {@link MongoClient}.
 *
 * @see App#login(Credentials)
 * @see io.realm.mongodb.sync.SyncConfiguration.Builder#Builder(User, String)
 */
@Beta
public class User extends StreamNetworkTransport {

    OsSyncUser osUser;
    private final App app;
    private ApiKeyAuth apiKeyAuthProvider = null;
    private MongoClient mongoClient = null;
    private Functions functions = null;
    private Push push = null;

    /**
     * The different types of users.
     */
    enum UserType {
        NORMAL("normal"),
        SERVER("server"),
        UNKNOWN("unknown");

        private final String key;

        UserType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * The user's potential states.
     */
    public enum State {
        LOGGED_IN(OsSyncUser.STATE_LOGGED_IN),
        REMOVED(OsSyncUser.STATE_REMOVED),
        LOGGED_OUT(OsSyncUser.STATE_LOGGED_OUT);

        private final byte nativeValue;

        State(byte nativeValue) {
            this.nativeValue = nativeValue;
        }

        byte getKey() {
            return nativeValue;
        }
    }

    private static class MongoClientImpl extends MongoClient {
        protected MongoClientImpl(OsMongoClient osMongoClient,
                                  StreamNetworkTransport streamNetworkTransport,
                                  CodecRegistry codecRegistry) {
            super(osMongoClient, streamNetworkTransport, codecRegistry);
        }
    }

    private static class PushImpl extends Push {
        protected PushImpl(OsPush osPush) {
            super(osPush);
        }
    }

    User(long nativePtr, App app) {
        this.osUser = new OsSyncUser(nativePtr);
        this.app = app;
    }

    /**
     * Returns the id of the user.
     *
     * @return the id of the user.
     */
    public String getId() {
        return osUser.getIdentity();
    }

    /**
     * Returns the name of the user.
     *
     * @return the name of the user.
     */
    public String getName() {
        return osUser.nativeGetName();
    }

    /**
     * Returns the email address of the user.
     *
     * @return the email address of the user or null if there is no email address associated with the user.
     * address.
     */
    @Nullable
    public String getEmail() {
        return osUser.getEmail();
    }

    /**
     * Returns the picture URL of the user.
     *
     * @return the picture URL of the user or null if there is no picture URL associated with the user.
     */
    @Nullable
    public String getPictureUrl() {
        return osUser.getPictureUrl();
    }

    /**
     * Return the first name of the user.
     *
     * @return the first name of the user or null if there is no first name associated with the user.
     */
    @Nullable
    public String getFirstName() {
        return osUser.getFirstName();
    }

    /**
     * Return the last name of the user.
     *
     * @return the last name of the user or null if there is no last name associated with the user.
     */
    @Nullable
    public String getLastName() {
        return osUser.getLastName();
    }

    /**
     * Returns the gender of the user.
     *
     * @return the gender of the user or null if there is no gender associated with the user.
     */
    @Nullable
    public String getGender() {
        return osUser.getGender();
    }

    /**
     * Returns the birthday of the user.
     *
     * @return the birthday of the user or null if there is no birthday associated with the user.
     */
    @Nullable
    public String getBirthday() {
        return osUser.getBirthday();
    }

    /**
     * Returns the minimum age of the user.
     *
     * @return the minimum age of the user or null if there is no minimum age associated with the user.
     */
    @Nullable
    public Long getMinAge() {
        String minAge = osUser.getMinAge();
        return (minAge == null) ? null : Long.parseLong(minAge);
    }

    /**
     * Returns the maximum age of the user.
     *
     * @return the maximum age of the user or null if there is no maximum age associated with the user.
     */
    @Nullable
    public Long getMaxAge() {
        String maxAge = osUser.getMaxAge();
        return (maxAge == null) ? null : Long.parseLong(maxAge);
    }

    /**
     * Returns a new list of the user's identities.
     *
     * @return the list of identities.
     *
     * @see UserIdentity
     */
    public List<UserIdentity> getIdentities() {
        Pair<String, String>[] osIdentities = osUser.getIdentities();
        List<UserIdentity> identities = new ArrayList<>(osIdentities.length);
        for (int i = 0; i < osIdentities.length; i++) {
            Pair<String, String> data = osIdentities[i];
            identities.add(new UserIdentity(data.first, data.second));
        }
        return identities;
    }

    /**
     * Returns the current access token for the user.
     *
     * @return the current access token.
     */
    public String getAccessToken() {
        return osUser.getAccessToken();
    }

    /**
     * Returns the current refresh token for the user.
     *
     * @return the current refresh token.
     */
    public String getRefreshToken() {
        return osUser.getRefreshToken();
    }

    /**
     * Returns a unique identifier for the device the user logged in to.
     *
     * @return a unique device identifier for the user.
     */
    public String getDeviceId() {
        return osUser.getDeviceId();
    }

    /**
     * Returns the {@link App} this user is associated with.
     *
     * @return the {@link App} this user is associated with.
     */
    public App getApp() {
        return app;
    }

    /**
     * Returns the {@link State} the user is in.
     *
     * @return the {@link State} of the user.
     */
    public State getState() {
        byte nativeState = osUser.getState();
        for (State state : State.values()) {
            if (state.nativeValue == nativeState) {
                return state;
            }
        }
        throw new IllegalStateException("Unknown state: " + nativeState);
    }

    /**
     * Return the custom user data associated with the user in the Realm App.
     * <p>
     * The data is only refreshed when the user's access token is refreshed or when explicitly
     * calling {@link #refreshCustomData()}.
     *
     * @return The custom user data associated with the user.
     */
    public Document getCustomData() {
        return osUser.getCustomData();
    }

    /**
     * Re-fetch custom user data from the Realm App.
     *
     * @return The updated custom user data associated with the user.
     * @throws AppException if the request failed in some way.
     */
    public Document refreshCustomData() {
        osUser.refreshCustomData();
        return getCustomData();
    }

    /**
     * Re-fetch custom user data from the Realm App asynchronously.
     * <p>
     * This is the asynchronous variant of {@link #refreshCustomData()}.
     *
     * @param callback The callback that will receive the result or any errors from the request.
     * @return The task representing the ongoing operation.
     *
     * @throws IllegalStateException if not called on a looper thread.
     */
    public RealmAsyncTask refreshCustomData(App.Callback<Document> callback) {
        Util.checkLooperThread("Asynchronous functions is only possible from looper threads.");
        return new Request<Document>(App.NETWORK_POOL_EXECUTOR, callback) {
            @Override
            public Document run() throws AppException {
                return refreshCustomData();
            }
        }.start();
    }


    /**
     * Returns true if the user is currently logged in.
     * Returns whether or not this user is still logged into the MongoDB Realm App.
     *
     * @return {@code true} if the user is logged in. {@code false} otherwise.
     * @return {@code true} if still logged in, {@code false} if not.
     */
    public boolean isLoggedIn() {
        return getState() == State.LOGGED_IN;
    }

    /**
     * Links the current user with a new user identity represented by the given credentials.
     * <p>
     * Linking a user with more credentials, mean the user can login either of these credentials.
     * It also makes it possible to "upgrade" an anonymous user by linking it with e.g.
     * Email/Password credentials.
     * <pre>
     * {@code
     * // Example
     * App app = new App("app-id")
     * User user = app.login(Credentials.anonymous());
     * user.linkCredentials(Credentials.emailPassword("email", "password"));
     * }
     * </pre>
     * <p>
     * Note: It is not possible to link two existing users of MongoDB Realm. The provided credentials
     * must not have been used by another user.
     *
     * @param credentials the credentials to link with the current user.
     * @throws IllegalStateException if no user is currently logged in.
     * @return the {@link User} the credentials were linked to.
     */
    public User linkCredentials(Credentials credentials) {
        Util.checkNull(credentials, "credentials");
        checkLoggedIn();
        AtomicReference<User> success = new AtomicReference<>(null);
        AtomicReference<AppException> error = new AtomicReference<>(null);
        nativeLinkUser(app.nativePtr, osUser.getNativePtr(), credentials.osCredentials.getNativePtr(), new OsJNIResultCallback<User>(success, error) {
            @Override
            protected User mapSuccess(Object result) {
                osUser = new OsSyncUser((long) result); // OS returns the updated user as a new one.
                return User.this;
            }
        });
        return ResultHandler.handleResult(success, error);
    }

    /**
     * Links the current user with a new user identity represented by the given credentials.
     * <p>
     * Linking a user with more credentials, mean the user can login either of these credentials.
     * It also makes it possible to "upgrade" an anonymous user by linking it with e.g.
     * Email/Password credentials.
     * <pre>
     * {@code
     * // Example
     * App app = new App("app-id")
     * User user = app.login(Credentials.anonymous());
     * user.linkCredentials(Credentials.emailPassword("email", "password"));
     * }
     * </pre>
     * <p>
     * Note: It is not possible to link two existing users of MongoDB Realm. The provided credentials
     * must not have been used by another user.
     *
     * @param credentials the credentials to link with the current user.
     * @param callback callback when user identities has been linked or it failed. The callback will
     * always happen on the same thread as this method is called on.
     * @throws IllegalStateException if called from a non-looper thread.
     */
    public RealmAsyncTask linkCredentialsAsync(Credentials credentials, App.Callback<User> callback) {
        Util.checkLooperThread("Asynchronous linking identities is only possible from looper threads.");
        return new Request<User>(App.NETWORK_POOL_EXECUTOR, callback) {
            @Override
            public User run() throws AppException {
                return linkCredentials(credentials);
            }
        }.start();
    }

    /**
     * Removes a users credentials from this device. If the user was currently logged in, they
     * will be logged out as part of the process. This is only a local change and does not
     * affect the user state on the server.
     *
     * @return user that was removed.
     * @throws AppException if called from the UI thread or if the user was logged in, but
     * could not be logged out.
     */
    public User remove() throws AppException {
        boolean loggedIn = isLoggedIn();
        AtomicReference<User> success = new AtomicReference<>(null);
        AtomicReference<AppException> error = new AtomicReference<>(null);
        nativeRemoveUser(app.nativePtr, osUser.getNativePtr(), new OsJNIResultCallback<User>(success, error) {
            @Override
            protected User mapSuccess(Object result) {
                return User.this;
            }
        });
        ResultHandler.handleResult(success, error);
        if (loggedIn) {
            app.notifyUserLoggedOut(this);
        }
        return this;
    }

    /**
     * Removes a user's credentials from this device. If the user was currently logged in, they
     * will be logged out as part of the process. This is only a local change and does not
     * affect the user state on the server.
     *
     * @param callback callback when removing the user has completed or failed. The callback will always
     * happen on the same thread as this method is called on.
     * @throws IllegalStateException if called from a non-looper thread.
     */
    public RealmAsyncTask removeAsync(App.Callback<User> callback) {
        Util.checkLooperThread("Asynchronous removal of users is only possible from looper threads.");
        return new Request<User>(App.NETWORK_POOL_EXECUTOR, callback) {
            @Override
            public User run() throws AppException {
                return remove();
            }
        }.start();
    }

    /**
     * Log the user out of the Realm App. This will unregister them on the device, stop any
     * synchronization to and from the users' Realms, and those Realms will be deleted next time
     * the app restarts. Therefor logging out should not be done until all changes to Realms have
     * been uploaded to the server.
     * <p>
     * Once the Realm App has confirmed the logout any registered {@link AuthenticationListener}
     * will be notified and user credentials will be deleted from this device.
     * <p>
     * Logging out anonymous users will remove them immediately instead of marking them as
     * {@link User.State#LOGGED_OUT}. All other users will be marked as {@link User.State#LOGGED_OUT}
     * and will still be returned by {@link App#allUsers()}. They can be removed completely by calling
     * {@link #remove()}.
     *
     * @throws AppException if an error occurred while trying to log the user out of the Realm
     * App.
     */
    public void logOut() throws AppException {
        boolean loggedIn = isLoggedIn();
        AtomicReference<AppException> error = new AtomicReference<>(null);
        nativeLogOut(app.nativePtr, osUser.getNativePtr(), new OsJNIVoidResultCallback(error));
        ResultHandler.handleResult(null, error);
        if (loggedIn) {
            app.notifyUserLoggedOut(this);
        }
    }

    /**
     * Log the user out of the Realm App asynchronously. This will unregister them on the device, stop any
     * synchronization to and from the users' Realms, and those Realms will be deleted next time
     * the app restarts. Therefor logging out should not be done until all changes to Realms have
     * been uploaded to the server.
     * <p>
     * Once the Realm App has confirmed the logout any registered {@link AuthenticationListener}
     * will be notified and user credentials will be deleted from this device.
     * <p>
     * Logging out anonymous users will remove them immediately instead of marking them as
     * {@link User.State#LOGGED_OUT}. All other users will be marked as {@link User.State#LOGGED_OUT}
     * and will still be returned by {@link App#allUsers()}. They can be removed completely by calling
     * {@link #remove()}.
     *
     * @param callback callback when logging out has completed or failed. The callback will always
     * happen on the same thread as this method is called on.
     * @throws IllegalStateException if called from a non-looper thread.
     */
    public RealmAsyncTask logOutAsync(App.Callback<User> callback) {
        Util.checkLooperThread("Asynchronous log out is only possible from looper threads.");
        return new Request<User>(App.NETWORK_POOL_EXECUTOR, callback) {
            @Override
            public User run() throws AppException {
                logOut();
                return User.this;
            }
        }.start();
    }

    /**
     * Returns a wrapper for managing API keys controlled by the current user.
     *
     * @return wrapper for managing API keys controlled by the current user.
     * @throws IllegalStateException if no user is currently logged in.
     */
    public synchronized ApiKeyAuth getApiKeyAuth() {
        checkLoggedIn();
        if (apiKeyAuthProvider == null) {
            apiKeyAuthProvider = new ApiKeyAuthImpl(this);
        }
        return apiKeyAuthProvider;
    }

    /**
     * Returns a <i>functions</i> manager for invoking MongoDB Realm Functions.
     * <p>
     * This will use the associated app's default codec registry to encode and decode arguments and
     * results.
     *
     * @see Functions
     */
    public synchronized Functions getFunctions() {
        checkLoggedIn();
        if (functions == null) {
            functions = new FunctionsImpl(this);
        }
        return functions;
    }

    /**
     * Returns a <i>functions</i> manager for invoking Realm Functions with custom
     * codec registry for encoding and decoding arguments and results.
     *
     * @param codecRegistry The codec registry to use for encoding and decoding arguments and results
     *                      towards the remote Realm App.
     * @see Functions
     */
    public Functions getFunctions(CodecRegistry codecRegistry) {
        return new FunctionsImpl(this, codecRegistry);
    }

    /**
     * Returns the {@link Push} instance for managing push notification registrations.
     *
     * @param serviceName        the service name used to connect to the server.
     */
    public synchronized Push getPush(String serviceName) {
        if (push == null) {
            OsPush osPush = new OsPush(app.nativePtr, osUser, serviceName);
            push = new PushImpl(osPush);
        }
        return push;
    }

    /**
     * Returns a {@link MongoClient} instance for accessing documents in the database.
     *
     * @param serviceName        the service name used to connect to the server.
     */
    public synchronized MongoClient getMongoClient(String serviceName) {
        Util.checkEmpty(serviceName, "serviceName");
        if (mongoClient == null) {
            OsMongoClient osMongoClient = new OsMongoClient(app.nativePtr, serviceName);
            mongoClient = new MongoClientImpl(osMongoClient, this, app.getConfiguration().getDefaultCodecRegistry());
        }
        return mongoClient;
    }

    @Override
    protected OsJavaNetworkTransport.Request makeStreamingRequest(String functionName, BsonArray bsonArgs, String serviceName){
        return app.makeStreamingRequest(osUser, functionName, bsonArgs, serviceName);
    }

    @Override
    public OsJavaNetworkTransport.Response sendRequest(OsJavaNetworkTransport.Request request) throws IOException {
        return app.networkTransport.sendStreamingRequest(request);
    }

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (!osUser.equals(user.osUser)) return false;
        return app.equals(user.app);
    }

    @Override
    public int hashCode() {
        int result = osUser.hashCode();
        result = 31 * result + app.hashCode();
        return result;
    }

    private void checkLoggedIn() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("User is not logged in.");
        }
    }

    private static native void nativeRemoveUser(long nativeAppPtr, long nativeUserPtr, OsJavaNetworkTransport.NetworkTransportJNIResultCallback callback);
    private static native void nativeLinkUser(long nativeAppPtr, long nativeUserPtr, long nativeCredentialsPtr, OsJavaNetworkTransport.NetworkTransportJNIResultCallback callback);
    private static native void nativeLogOut(long appNativePtr, long userNativePtr, OsJavaNetworkTransport.NetworkTransportJNIResultCallback callback);
}
