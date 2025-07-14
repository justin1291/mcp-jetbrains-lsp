package com.example.demo;

import java.util.*;

/**
 * REST API controller for user operations
 */
public class ApiController {
    
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_ERROR = "ERROR";
    
    private final UserService userService;
    
    public ApiController(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * Creates a new user
     * @param name User's name
     * @param email User's email
     * @return API response with the created user
     */
    public ApiResponse<User> createUser(String name, String email) {
        String id = UUID.randomUUID().toString();
        User user = new User(id, name, email);
        
        if (userService.addUser(user)) {
            return new ApiResponse<>(STATUS_SUCCESS, "User created", user);
        } else {
            return new ApiResponse<>(STATUS_ERROR, "Failed to create user", null);
        }
    }
    
    /**
     * Gets a user by ID
     * @param userId The user ID
     * @return API response with the user
     */
    public ApiResponse<User> getUser(String userId) {
        Optional<User> user = userService.findUser(userId);
        
        if (user.isPresent()) {
            return new ApiResponse<>(STATUS_SUCCESS, "User found", user.get());
        } else {
            return new ApiResponse<>(STATUS_ERROR, "User not found", null);
        }
    }
    
    /**
     * Updates an existing user
     * @param userId The user ID
     * @param name New name
     * @param email New email
     * @return API response with the updated user
     */
    public ApiResponse<User> updateUser(String userId, String name, String email) {
        Optional<User> existingUser = userService.findUser(userId);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setName(name);
            user.setEmail(email);
            return new ApiResponse<>(STATUS_SUCCESS, "User updated", user);
        } else {
            return new ApiResponse<>(STATUS_ERROR, "User not found", null);
        }
    }
    
    /**
     * Deletes a user
     * @param userId The user ID to delete
     * @return API response indicating success or failure
     */
    public ApiResponse<Void> deleteUser(String userId) {
        if (userService.removeUser(userId)) {
            return new ApiResponse<>(STATUS_SUCCESS, "User deleted", null);
        } else {
            return new ApiResponse<>(STATUS_ERROR, "User not found", null);
        }
    }
    
    /**
     * Gets all users
     * @return API response with list of all users
     */
    public ApiResponse<List<User>> getAllUsers() {
        List<User> allUsers = new ArrayList<>();
        // In a real implementation, UserService would have a getAllUsers method
        return new ApiResponse<>(STATUS_SUCCESS, "Users retrieved", allUsers);
    }
    
    /**
     * Generic API response wrapper
     * @param <T> The type of data in the response
     */
    public static class ApiResponse<T> {
        private final String status;
        private final String message;
        private final T data;
        private final long timestamp;
        
        public ApiResponse(String status, String message, T data) {
            this.status = status;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public T getData() {
            return data;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isSuccess() {
            return STATUS_SUCCESS.equals(status);
        }
    }
}
