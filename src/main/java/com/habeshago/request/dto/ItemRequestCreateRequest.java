package com.habeshago.request.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class ItemRequestCreateRequest {

    @NotBlank
    private String description;

    private BigDecimal weightKg;

    private String specialInstructions;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
}
