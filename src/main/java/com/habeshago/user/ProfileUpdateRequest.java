package com.habeshago.user;

/**
 * Request DTO for updating user profile.
 * All fields are optional - only provided fields will be updated.
 */
public class ProfileUpdateRequest {

    private String firstName;
    private String lastName;
    private String username;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
