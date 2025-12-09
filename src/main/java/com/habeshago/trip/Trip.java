package com.habeshago.trip;

import com.habeshago.request.ItemRequest;
import com.habeshago.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trip_search", columnList = "from_city,to_city,departure_date"),
        @Index(name = "idx_trip_user", columnList = "user_id")
})
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "from_city", nullable = false, length = 100)
    private String fromCity;

    @Column(name = "from_country", length = 100)
    private String fromCountry;

    @Column(name = "from_airport_code", length = 10)
    private String fromAirportCode;

    @Column(name = "to_city", nullable = false, length = 100)
    private String toCity;

    @Column(name = "to_country", length = 100)
    private String toCountry;

    @Column(name = "to_airport_code", length = 10)
    private String toAirportCode;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacity_type", nullable = false, length = 20)
    private CapacityType capacityType;

    @Column(name = "max_weight_kg")
    private BigDecimal maxWeightKg;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status = TripStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "trip", fetch = FetchType.LAZY)
    private List<ItemRequest> requests = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

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

    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<ItemRequest> getRequests() { return requests; }
    public void setRequests(List<ItemRequest> requests) { this.requests = requests; }
}
