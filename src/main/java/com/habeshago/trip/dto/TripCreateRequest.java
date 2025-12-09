package com.habeshago.trip.dto;

import com.habeshago.trip.CapacityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TripCreateRequest {

    @NotBlank
    @Size(max = 100, message = "City name must be at most 100 characters")
    private String fromCity;

    @Size(max = 100, message = "Country name must be at most 100 characters")
    private String fromCountry;

    @Size(max = 10, message = "Airport code must be at most 10 characters")
    private String fromAirportCode;

    @NotBlank
    @Size(max = 100, message = "City name must be at most 100 characters")
    private String toCity;

    @Size(max = 100, message = "Country name must be at most 100 characters")
    private String toCountry;

    @Size(max = 10, message = "Airport code must be at most 10 characters")
    private String toAirportCode;

    @NotNull
    @FutureOrPresent(message = "Departure date must be today or in the future")
    private LocalDate departureDate;

    private LocalDate arrivalDate;

    @NotNull
    private CapacityType capacityType;

    @DecimalMin(value = "0.1", message = "Max weight must be at least 0.1 kg")
    @DecimalMax(value = "100.0", message = "Max weight cannot exceed 100 kg")
    private BigDecimal maxWeightKg;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    // getters and setters
    public String getFromCity() { return fromCity; }
    public void setFromCity(String fromCity) { this.fromCity = fromCity; }
    public String getFromCountry() { return fromCountry; }
    public void setFromCountry(String fromCountry) { this.fromCountry = fromCountry; }
    public String getFromAirportCode() { return fromAirportCode; }
    public void setFromAirportCode(String fromAirportCode) { this.fromAirportCode = fromAirportCode; }
    public String getToCity() { return toCity; }
    public void setToCity(String toCity) { this.toCity = toCity; }
    public String getToCountry() { return toCountry; }
    public void setToCountry(String toCountry) { this.toCountry = toCountry; }
    public String getToAirportCode() { return toAirportCode; }
    public void setToAirportCode(String toAirportCode) { this.toAirportCode = toAirportCode; }
    public LocalDate getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
    public CapacityType getCapacityType() { return capacityType; }
    public void setCapacityType(CapacityType capacityType) { this.capacityType = capacityType; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public void setMaxWeightKg(BigDecimal maxWeightKg) { this.maxWeightKg = maxWeightKg; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
