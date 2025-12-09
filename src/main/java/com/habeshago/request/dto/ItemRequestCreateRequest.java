package com.habeshago.request.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ItemRequestCreateRequest {

    @NotBlank
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @DecimalMin(value = "0.01", message = "Weight must be at least 0.01 kg")
    @DecimalMax(value = "100.00", message = "Weight cannot exceed 100 kg")
    private BigDecimal weightKg;

    @Size(max = 500, message = "Special instructions must be at most 500 characters")
    private String specialInstructions;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
}
