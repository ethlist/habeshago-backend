package com.habeshago.trip.dto;

import jakarta.validation.constraints.Size;

public class TripCancelRequest {

    @Size(max = 500, message = "Cancellation reason must be at most 500 characters")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
