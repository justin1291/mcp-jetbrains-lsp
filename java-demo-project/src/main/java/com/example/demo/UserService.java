package com.example.demo;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class for managing users.
 * This class demonstrates various Java features for testing symbol extraction.
 * @since 1.0
 * @see DataProcessor
 */
@SuppressWarnings("unused")
public class UserService {
    
    /**
     * Default role assigned to new users
     */
    public static final String DEFAULT_ROLE = "USER";
    
    /**
     * Maximum number of users allowed
     * @deprecated Use dynamic limits instead
     */
    @Deprecated
    public static final int MAX_USERS = 1000;
    
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    
    /**
     * Event types for user operations
     */
    public enum UserEvent {
        CREATED("User created"),
        UPDATED("User updated"),
        DELETED("User deleted");
        
        private final String description;
        
        UserEvent(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Listener interface for user events
     */
    public interface UserListener {
        void onUserEvent(User user, UserEvent event);
    }
    
    private final List<UserListener> listeners = new ArrayList<>();
    
    /**
     * Adds a new user to the service
     * @param user The user to add
     * @return true if the user was added successfully
     */
    public boolean addUser(User user) {
        lock.lock();
        try {
            if (users.containsKey(user.getId())) {
                return false;
            }
            users.put(user.getId(), user);
            notifyListeners(user, UserEvent.CREATED);
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Finds a user by their ID
     * @param userId The user ID to search for
     * @return An Optional containing the user if found
     */
    public Optional<User> findUser(String userId) {
        return Optional.ofNullable(users.get(userId));
    }
    
    /**
     * Removes a user from the service
     * @param userId The ID of the user to remove
     * @return true if the user was removed
     */
    public boolean removeUser(String userId) {
        lock.lock();
        try {
            User removed = users.remove(userId);
            if (removed != null) {
                notifyListeners(removed, UserEvent.DELETED);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Gets the total count of users
     * @return The number of users
     */
    public int getUserCount() {
        return users.size();
    }
    
    /**
     * Validates if a user object is valid
     * @param user The user to validate
     * @return true if the user is valid
     */
    public static boolean isValidUser(User user) {
        return user != null && 
               user.getId() != null && 
               user.getName() != null &&
               !user.getName().trim().isEmpty();
    }
    
    /**
     * @deprecated Use findUser(String) instead
     */
    @Deprecated
    public User getUser(String userId) {
        return users.get(userId);
    }
    
    protected void notifyListeners(User user, UserEvent event) {
        for (UserListener listener : listeners) {
            listener.onUserEvent(user, event);
        }
    }
    
    void addListener(UserListener listener) {
        listeners.add(listener);
    }
    
    private void validateUser(User user) {
        if (!isValidUser(user)) {
            throw new IllegalArgumentException("Invalid user");
        }
    }
    
    /**
     * Inner class for managing user sessions
     */
    public static class UserSession {
        private final String sessionId;
        private final String userId;
        private final long createdAt;
        
        public UserSession(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.createdAt = System.currentTimeMillis();
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
    }
}
