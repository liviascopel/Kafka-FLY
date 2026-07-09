package br.ufes.soe.config;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.pickup.PickupAlert;
import br.ufes.soe.domain.pickup.PickupRequest;
import br.ufes.soe.domain.ranking.AirlineMetrics;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.domain.weather.ClimateExposureAlert;
import br.ufes.soe.domain.weather.WeatherAlert;
import br.ufes.soe.domain.weather.WeatherInterval;

public class JsonSerdes {

    public static Serde<Flight> flight() {
        JacksonJsonSerializer<Flight> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<Flight> deserializer = new JacksonJsonDeserializer<>(Flight.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<AirportWeather> airportWeather() {
        JacksonJsonSerializer<AirportWeather> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<AirportWeather> deserializer = new JacksonJsonDeserializer<>(AirportWeather.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<WeatherAlert> weatherAlert() {
        JacksonJsonSerializer<WeatherAlert> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<WeatherAlert> deserializer = new JacksonJsonDeserializer<>(WeatherAlert.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<AirlineMetrics> airlineMetrics() {
        JacksonJsonSerializer<AirlineMetrics> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<AirlineMetrics> deserializer = new JacksonJsonDeserializer<>(AirlineMetrics.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<WeatherInterval> weatherInterval() {
        JacksonJsonSerializer<WeatherInterval> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<WeatherInterval> deserializer = new JacksonJsonDeserializer<>(WeatherInterval.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<ClimateExposureAlert> climateExposureAlert() {
        JacksonJsonSerializer<ClimateExposureAlert> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<ClimateExposureAlert> deserializer = new JacksonJsonDeserializer<>(ClimateExposureAlert.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<PickupRequest> pickupRequest() {
        JacksonJsonSerializer<PickupRequest> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<PickupRequest> deserializer = new JacksonJsonDeserializer<>(PickupRequest.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<PickupAlert> pickupAlert() {
        JacksonJsonSerializer<PickupAlert> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<PickupAlert> deserializer = new JacksonJsonDeserializer<>(PickupAlert.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }
}