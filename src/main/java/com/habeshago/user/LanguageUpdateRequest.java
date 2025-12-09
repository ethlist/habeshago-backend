package com.habeshago.user;

import jakarta.validation.constraints.NotBlank;

public class LanguageUpdateRequest {

    @NotBlank
    private String language;

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
