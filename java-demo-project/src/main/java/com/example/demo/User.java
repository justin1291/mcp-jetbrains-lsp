package com.example.demo;

import java.util.Objects;

/**
 * User entity class
 */
public class User extends BaseEntity {
    private String id;
    private String name;
    private String email;
    private String role;
    
    /**
     * @deprecated Use DEFAULT_ROLE from UserService
     */
    @Deprecated
    protected static final String OLD_DEFAULT_ROLE = "GUEST";
    
    /**
     * Default constructor
     */
    public User() {
        this.role = UserService.DEFAULT_ROLE;
    }
    
    /**
     * Creates a new user with the specified details
     * @param id The user ID
     * @param name The user's name
     * @param email The user's email
     */
    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = UserService.DEFAULT_ROLE;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    /**
     * Checks if this user has admin privileges
     * @return true if the user is an admin
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * Returns string representation of the user
     */
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
    
    /**
     * Validates this user instance
     * @deprecated Use UserService.isValidUser() instead
     */
    @Deprecated
    @SuppressWarnings("unused")
    protected boolean validate() {
        return id != null && name != null && email != null;
    }
    
    private void updateTimestamp() {
        // Private helper method
        setLastModified(System.currentTimeMillis());
    }
    
    void notifyChange() {
        // Package-private method
        updateTimestamp();
    }
}
