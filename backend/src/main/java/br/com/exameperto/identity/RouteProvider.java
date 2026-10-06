package br.com.exameperto.identity;

import java.time.Instant;

interface RouteProvider {
    RouteResult route(Address origin, Address destination);
    record RouteResult(String provider, String reference, int distanceMeters, int durationSeconds,
                       boolean trafficIncluded, Instant calculatedAt) {}
}
