package com.habeshago.user;

import com.habeshago.user.dto.TravelerProfileDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travelers")
public class TravelerController {

    private final TravelerService travelerService;

    public TravelerController(TravelerService travelerService) {
        this.travelerService = travelerService;
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<TravelerProfileDto> getTravelerProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(travelerService.getTravelerProfile(userId));
    }
}
