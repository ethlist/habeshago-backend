package com.habeshago.trip.dto;

import com.habeshago.trip.CapacityType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TripCreateRequest {

    @NotBlank
    private String fromCity;

    private String fromCountry;
    private String fromAirportCode;

    @NotBlank
    private String toCity;

    private String toCountry;
    private String toAirportCode;

    @NotNull
    @FutureOrPresent
    private LocalDate departureDate;

    private LocalDate arrivalDate;

    @NotNull
    private CapacityType capacityType;

    private BigDecimal maxWeightKg;

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
