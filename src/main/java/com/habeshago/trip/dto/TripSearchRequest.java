package com.habeshago.trip.dto;

import com.habeshago.trip.CapacityType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class TripSearchRequest {

    @NotBlank
    private String fromCity;

    @NotBlank
    private String toCity;

    @NotNull
    @FutureOrPresent
    private LocalDate date;

    private CapacityType capacityType;

    // getters and setters
    public String getFromCity() { return fromCity; }
    public void setFromCity(String fromCity) { this.fromCity = fromCity; }
    public String getToCity() { return toCity; }
    public void setToCity(String toCity) { this.toCity = toCity; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public CapacityType getCapacityType() { return capacityType; }
    public void setCapacityType(CapacityType capacityType) { this.capacityType = capacityType; }
}
