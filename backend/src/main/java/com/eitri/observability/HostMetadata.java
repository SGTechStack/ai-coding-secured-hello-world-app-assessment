package com.eitri.observability;

/** Safe instance identity included in the application-ready event. */
public record HostMetadata(String name, String address) {}
