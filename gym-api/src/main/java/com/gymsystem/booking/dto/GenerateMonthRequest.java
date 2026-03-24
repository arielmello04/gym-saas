package com.gymsystem.booking.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Admin payload to generate a month worth of sessions from business hours.
 */
@Data
public class GenerateMonthRequest {

    @Min(2000) @Max(2100)
    private int year;           // e.g. 2025

    @Min(1) @Max(12)
    private int month;          // 1..12

    @NotBlank
    private String classTypeCode;

    @Min(15) @Max(480)
    private int slotMinutes;    // duration of each session

    @Min(1) @Max(1000)
    private int capacity;       // capacity per session

    /** Start time of each slot, e.g. "07:00" */
    @NotBlank
    private String startTime;

    /** End time of each slot, e.g. "08:00" */
    @NotBlank
    private String endTime;

    /** IANA timezone, e.g. "America/Sao_Paulo". Defaults to UTC if null. */
    private String timezone;

    /**
     * Days of week to generate (1=Mon … 7=Sun, ISO-8601).
     * If null or empty, all days of the month are included.
     */
    private List<Integer> daysOfWeek;
}
