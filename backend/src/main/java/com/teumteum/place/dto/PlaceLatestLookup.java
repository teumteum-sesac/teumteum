package com.teumteum.place.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record PlaceLatestLookup(Status status, OffsetDateTime fetchedAt, LatestPlaceResponse place) {
    public enum Status { SUCCESS, NOT_FOUND, FAILED }
    public static PlaceLatestLookup success(LatestPlaceResponse place) {
        return new PlaceLatestLookup(Status.SUCCESS, OffsetDateTime.now(ZoneId.of("Asia/Seoul")), place);
    }
    public static PlaceLatestLookup notFound() { return new PlaceLatestLookup(Status.NOT_FOUND, null, null); }
    public static PlaceLatestLookup failed() { return new PlaceLatestLookup(Status.FAILED, null, null); }
}
